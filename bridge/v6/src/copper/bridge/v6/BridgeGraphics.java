package copper.bridge.v6;

import copper.bridge.annotation.BridgeOverride;

import arc.*;
import arc.Graphics.Cursor.*;
import arc.graphics.*;
import arc.graphics.gl.*;

import copper.bridge.*;
import copper.bridge.BridgeOptions.GLMode;
import copper.bridge.jvm.*;
import copper.bridge.util.*;
import org.lwjgl.*;
import org.lwjgl.egl.*;
import org.lwjgl.opengles.*;
import org.lwjgl.system.*;

import java.io.*;
import java.nio.*;

/**
 * arc's graphics for the JVM side of the bridge. {@code android.opengl} is unreachable in this JVM, so LWJGL's EGL
 * and OpenGL ES bindings are the only route to GL, and ART hands the window over as an opaque native pointer. A
 * pause destroys only the window surface, so GL objects survive a resume.
 */
public class BridgeGraphics extends Graphics {
    /** Coverage sampling attribute; not in LWJGL's core EGL headers. */
    private static final int EGL_COVERAGE_SAMPLES_NV = 0x30E1;

    private static final String STAGING_FOLDER = "native/angle";

    private static final String ANGLE_EGL = "libEGL_angle.so";
    private static final String ANGLE_GLES = "libGLESv2_angle.so";

    private static final String SYSTEM_EGL = "libEGL.so";
    private static final String SYSTEM_GLES = "libGLESv2.so";

    /** Where a device's own ANGLE may sit, in the order the search tries them. */
    private static final String[] SYSTEM_LIBRARY_ROOTS = {
            "/system", "/apex/com.android.angle", "/system_ext", "/vendor", "/product", "/odm"
    };

    private long eglDisplay = EGL10.EGL_NO_DISPLAY;
    private long eglContext = EGL10.EGL_NO_CONTEXT;
    private long eglSurface = EGL10.EGL_NO_SURFACE;

    private GL20 gl20;
    private GL30 gl30;
    private GLVersion glVersion;
    private String extensions;

    private int width;
    private int height;
    private int safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom;

    private long frameId = -1;
    private long lastFrameTime = -1;
    private long frameCounterStart = System.nanoTime();
    private int frames;
    private int fps;
    private float deltaTime;

    /** Set when the context was made current again, so the first delta of a resume is not huge. */
    private boolean resumed;

    private BufferFormat bufferFormat = new BufferFormat(8, 8, 8, 0, 16, 0, 0, false);

    public BridgeGraphics() {
    }

    /**
     * Points LWJGL at this device's EGL and GLES libraries and loads them; LWJGL's own bundled natives do not exist
     * for Android. A pair that is not there is reported and the system's used instead.
     */
    public void configure() {
        prepareLwjglNatives();

        String egl = SYSTEM_EGL;
        String gles = SYSTEM_GLES;

        if (Bridge.options.angle) {
            if (Bridge.options.anglePath != null) {
                Log.verbose("GL", "using angle provided by user");
                File eglAngle = Libraries.find(Bridge.options.anglePath, Bridge.options.abi, ANGLE_EGL, SYSTEM_EGL);
                File glesAngle = Libraries.find(Bridge.options.anglePath, Bridge.options.abi, ANGLE_GLES, SYSTEM_GLES);
                if (eglAngle != null && glesAngle != null) {
                    // W^X: LWJGL opens these by absolute path, so they have to be executable and not writable.
                    Libraries.ensureLoadable(eglAngle);
                    Libraries.ensureLoadable(glesAngle);

                    egl = eglAngle.getAbsolutePath();
                    gles = glesAngle.getAbsolutePath();
                } else {
                    Log.warn("GL", "ANGLE was requested but no EGL/GLES pair was found under "
                            + Bridge.options.anglePath + "; using the non-angle libraries");
                }
            } else {
                Log.verbose("GL", "using system angle");
                File[] device = findSystemAngle();
                if (device != null) {
                    egl = stageAngle(device[0]).getAbsolutePath();
                    gles = stageAngle(device[1]).getAbsolutePath();
                } else {
                    Log.warn("GL", "ANGLE was requested but this device has no " + ANGLE_EGL
                            + "/" + ANGLE_GLES + "; using the non-angle libraries");
                }
            }
        }

        Configuration.DISABLE_HASH_CHECKS.set(true);
        Configuration.EGL_LIBRARY_NAME.set(egl);
        Configuration.OPENGLES_LIBRARY_NAME.set(gles);
        Configuration.OPENGLES_EXPLICIT_INIT.set(true);
        // LWJGL's debug output follows the two flags as arc's level does: either one asks for everything
        if (Bridge.options.debug || Bridge.options.verbose)
            Configuration.DEBUG.set(true);

        Log.info("GL", "EGL library: " + egl);
        Log.info("GL", "GLES library: " + gles);
        GLES.create();
    }

    /** Unpacks the LWJGL natives from the bridge jar and points both loaders at them: LWJGL's own search path, and
     *  {@code java.library.path} for arc's {@code SharedLibraryLoader}, which would otherwise extract the game jar's
     *  Linux copy. */
    private void prepareLwjglNatives() {
        File lwjgl = LwjglNatives.extract();
        if (lwjgl == null)
            return;
        Configuration.LIBRARY_PATH.set(lwjgl.getAbsolutePath());
    }

    /** Finds the device's own ANGLE pair: the system's library directories in turn, the first holding both winning. */
    private static File[] findSystemAngle() {
        String suffix = bits64() ? "lib64" : "lib";
        for (String root : SYSTEM_LIBRARY_ROOTS) {
            File dir = new File(root + "/" + suffix);
            File egl = new File(dir, ANGLE_EGL);
            File gles = new File(dir, ANGLE_GLES);
            if (egl.isFile() && gles.isFile())
                return new File[] {egl, gles};
        }
        return null;
    }

    /** Copies a device ANGLE library into {@link #STAGING_FOLDER}, which is what makes it loadable: the linker only
     *  lets an app process load the platform libraries {@code public.libraries.txt} names. */
    private static File stageAngle(File source) {
        File stageFolder = new File(Bridge.options.cacheFolder, STAGING_FOLDER);
        File target = new File(stageFolder, source.getName());
        Libraries.extract(() -> new FileInputStream(source), target);
        Log.verbose("GL", "staged " + target.getName());
        return target;
    }

    private static boolean bits64() {
        String arch = Bridge.options.arch == null ? "" : Bridge.options.arch;
        String abi = Bridge.options.abi == null ? "" : Bridge.options.abi;
        return arch.contains("64") || arch.startsWith("armv8") || abi.contains("64");
    }

    /** Creates the context and window surface for the window ART handed over, or replaces only the surface. */
    public boolean createSurface(long window, int surfaceWidth, int surfaceHeight) {
        if (eglDisplay == EGL10.EGL_NO_DISPLAY || eglContext == EGL10.EGL_NO_CONTEXT) {
            fullInit(window);
        } else {
            createWindowSurface(window);
        }

        if (!EGL10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            Log.error("GL", "eglMakeCurrent failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
            return false;
        }

        width = surfaceWidth;
        height = surfaceHeight;

        if (gl20 == null)
            setupGL();

        GLES20.glViewport(0, 0, width, height);

        lastFrameTime = System.nanoTime();
        resumed = true;
        return true;
    }

    private void fullInit(long window) {
        eglDisplay = EGL10.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY)
            throw new IllegalStateException("eglGetDisplay failed");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!EGL10.eglInitialize(eglDisplay, stack.mallocInt(1), stack.mallocInt(1)))
                throw new IllegalStateException("eglInitialize failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
        }

        long config = chooseConfig();
        logConfig(config);

        // An explicit request is honoured; with none, the highest version this device can give is tried, and
        // only a failed creation steps down.
        eglContext = createContext(config, Bridge.options.glMode == GLMode.GL2 ? 2 : 3);
        if (eglContext == EGL10.EGL_NO_CONTEXT && Bridge.options.glMode == GLMode.Auto) {
            Log.warn("GL", "no OpenGL ES 3 context; falling back to OpenGL ES 2");
            eglContext = createContext(config, 2);
        }
        if (eglContext == EGL10.EGL_NO_CONTEXT)
            throw new IllegalStateException("eglCreateContext failed: 0x" + Integer.toHexString(EGL10.eglGetError())
                    + (Bridge.options.glMode == GLMode.Auto ? "" : " (an OpenGL ES "
                    + (Bridge.options.glMode == GLMode.GL2 ? "2" : "3") + " context was requested)"));

        createWindowSurface(window);
    }

    /** Creates a context of exactly this ES version: the config's renderable bit is not the context version. */
    private long createContext(long config, int esVersion) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // ES2 is requested even for an ES3 context: every driver that can give an ES3 context
            // reports the ES2 bit, and asking for an ES3 bit is not portable.
            int[] attribs = {EGL13.EGL_CONTEXT_CLIENT_VERSION, esVersion, EGL10.EGL_NONE};
            return EGL10.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, stack.ints(attribs));
        }
    }

    private void createWindowSurface(long window) {
        long config = chooseConfig();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            eglSurface = EGL10.eglCreateWindowSurface(eglDisplay, config, window, stack.ints(EGL10.EGL_NONE));
        }
        if (eglSurface == EGL10.EGL_NO_SURFACE)
            throw new IllegalStateException("eglCreateWindowSurface failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
    }

    private long chooseConfig() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] attribs = {
                    EGL12.EGL_RENDERABLE_TYPE, EGL13.EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 0,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                    EGL10.EGL_NONE
            };
            PointerBuffer configs = stack.mallocPointer(1);
            IntBuffer count = stack.mallocInt(1);
            if (!EGL10.eglChooseConfig(eglDisplay, stack.ints(attribs), configs, count))
                throw new IllegalStateException("eglChooseConfig failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
            if (count.get(0) <= 0)
                throw new IllegalStateException("no EGL config supports an OpenGL ES window");
            return configs.get(0);
        }
    }

    private void logConfig(long config) {
        int r = attrib(config, EGL10.EGL_RED_SIZE);
        int g = attrib(config, EGL10.EGL_GREEN_SIZE);
        int b = attrib(config, EGL10.EGL_BLUE_SIZE);
        int a = attrib(config, EGL10.EGL_ALPHA_SIZE);
        int d = attrib(config, EGL10.EGL_DEPTH_SIZE);
        int s = attrib(config, EGL10.EGL_STENCIL_SIZE);
        int samples = Math.max(attrib(config, EGL10.EGL_SAMPLES), attrib(config, EGL_COVERAGE_SAMPLES_NV));
        boolean coverage = attrib(config, EGL_COVERAGE_SAMPLES_NV) != 0;

        bufferFormat = new BufferFormat(r, g, b, a, d, s, samples, coverage);
        Log.info("GL", "framebuffer (" + r + ", " + g + ", " + b + ", " + a + ") depth " + d
                + " stencil " + s + " samples " + samples);
    }

    private int attrib(long config, int attribute) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer value = stack.mallocInt(1);
            return EGL10.eglGetConfigAttrib(eglDisplay, config, attribute, value) ? value.get(0) : 0;
        }
    }

    private static int esMajor(String version) {
        String prefix = "OpenGL ES ";
        int start = version.indexOf(prefix);
        if (start < 0)
            return 0;
        start += prefix.length();
        int end = start;
        while (end < version.length() && Character.isDigit(version.charAt(end)))
            end++;
        return end == start ? 0 : Integer.parseInt(version.substring(start, end));
    }

    /** {@code arc.graphics.Gl} does not exist at the range's floor; there is no static batch state to reset there. */
    private static void resetGl() {
        try {
            Class.forName("arc.graphics.Gl").getMethod("reset").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    /** The ES3 wrapper must not be installed on a device that only offers ES2. */
    private void setupGL() {
        GLES.createCapabilities();

        gl20 = new BridgeGL20();
        Core.gl = gl20;
        Core.gl20 = gl20;

        String version = gl20.glGetString(GL20.GL_VERSION);
        String vendor = gl20.glGetString(GL20.GL_VENDOR);
        String renderer = gl20.glGetString(GL20.GL_RENDERER);
        // Ordinal, not the constant: its spelling changes before v8, the order does not.
        glVersion = new GLVersion(Application.ApplicationType.values()[0], version, vendor, renderer);

        // GLVersion exposes atLeast/majorVersion only on the newer end and getMajorVersion on the older one, so
        // read the version string itself.
        boolean supportsES3 = esMajor(version) >= 3;
        // an explicit request is not quietly downgraded: the caller asked for a version this context cannot be
        if (Bridge.options.glMode == GLMode.GL3 && !supportsES3)
            throw new IllegalStateException("OpenGL ES 3 was requested, but the context is " + version);

        if (Bridge.options.glMode != GLMode.GL2 && supportsES3) {
            gl30 = new BridgeGL30();
            gl20 = gl30;
            Core.gl = gl30;
            Core.gl20 = gl30;
            Core.gl30 = gl30;
        }

        resetGl();
        Log.info("GL", "renderer: " + renderer);
        Log.info("GL", "vendor: " + vendor);
        Log.info("GL", "version: " + version);
        Log.info("GL", "using " + (gl30 != null ? "OpenGL ES 3" : "OpenGL ES 2") + " context");
    }

    /** Releases the window surface but keeps the context, so a resume does not rebuild its objects. */
    public void destroySurface() {
        if (eglDisplay == EGL10.EGL_NO_DISPLAY)
            return;

        EGL10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        if (eglSurface != EGL10.EGL_NO_SURFACE) {
            EGL10.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL10.EGL_NO_SURFACE;
        }
    }

    public void disposeContext() {
        destroySurface();
        if (eglDisplay == EGL10.EGL_NO_DISPLAY)
            return;

        if (eglContext != EGL10.EGL_NO_CONTEXT) {
            EGL10.eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL10.EGL_NO_CONTEXT;
        }
        EGL10.eglTerminate(eglDisplay);
        eglDisplay = EGL10.EGL_NO_DISPLAY;
    }

    public void surfaceResized(int surfaceWidth, int surfaceHeight) {
        width = surfaceWidth;
        height = surfaceHeight;
        if (gl20 != null)
            GLES20.glViewport(0, 0, width, height);
    }

    /** Marks the next frame as the first after a resume: a pause can last minutes, and its time would otherwise reach
     *  the game as one delta and step its physics in one jump. */
    public void markResumed() {
        resumed = true;
    }

    /** v8 asks its Graphics for this; v6 has neither a caller nor the interface method, so no {@code @BridgeOverride}. */
    public void safeInsetsUpdated(int top, int bottom, int left, int right) {
        safeInsetTop = top;
        safeInsetBottom = bottom;
        safeInsetLeft = left;
        safeInsetRight = right;
    }

    /** @see #safeInsetsUpdated; no {@code @BridgeOverride} for the same reason. */
    public int[] getSafeInsets() {
        return new int[]{safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom};
    }

    public void beginFrame() {
        long time = System.nanoTime();
        if (lastFrameTime == -1)
            lastFrameTime = time;

        deltaTime = (time - lastFrameTime) / 1000000000.0f;
        lastFrameTime = time;

        if (resumed) {
            deltaTime = 0f;
            resumed = false;
        }
        if (deltaTime == 0f)
            deltaTime = 1f / 60f;

        if (time - frameCounterStart >= 1000000000L) {
            fps = frames;
            frames = 0;
            frameCounterStart = time;
        }
        frames++;
        frameId++;
    }

    public void swapBuffers() {
        if (eglSurface != EGL10.EGL_NO_SURFACE)
            EGL10.eglSwapBuffers(eglDisplay, eglSurface);
    }

    @BridgeOverride
    public GL20 getGL20() {
        return gl20;
    }

    /** Abstract on the older end of the range, concrete on the newer one; the same answer either way. */
    @BridgeOverride
    public boolean isGL30Available() {
        return gl30 != null;
    }

    @BridgeOverride
    public void setGL20(GL20 gl20) {
        this.gl20 = gl20;
        if (gl30 == null) {
            Core.gl = gl20;
            Core.gl20 = gl20;
        }
    }

    @BridgeOverride
    public GL30 getGL30() {
        return gl30;
    }

    @BridgeOverride
    public void setGL30(GL30 gl30) {
        this.gl30 = gl30;
        if (gl30 != null) {
            this.gl20 = gl30;
            Core.gl = gl30;
            Core.gl20 = gl30;
            Core.gl30 = gl30;
        }
    }

    @BridgeOverride
    public int getWidth() {
        return width;
    }

    @BridgeOverride
    public int getHeight() {
        return height;
    }

    @BridgeOverride
    public int getBackBufferWidth() {
        return width;
    }

    @BridgeOverride
    public int getBackBufferHeight() {
        return height;
    }

    @BridgeOverride
    public long getFrameId() {
        return frameId;
    }

    @BridgeOverride
    public float getDeltaTime() {
        return deltaTime;
    }

    /** Abstract on the older end of the range, removed before its newer one: no {@code @BridgeOverride} on purpose. */
    @SuppressWarnings("unused")
    @BridgeOverride
    public float getRawDeltaTime() {
        return deltaTime;
    }

    @BridgeOverride
    public int getFramesPerSecond() {
        return fps;
    }

    @BridgeOverride
    public GLVersion getGLVersion() {
        return glVersion;
    }

    @BridgeOverride
    public float getPpiX() {
        return Bridge.options.xdpi;
    }

    @BridgeOverride
    public float getPpiY() {
        return Bridge.options.ydpi;
    }

    @BridgeOverride
    public float getPpcX() {
        return Bridge.options.xdpi / 2.54f;
    }

    @BridgeOverride
    public float getPpcY() {
        return Bridge.options.ydpi / 2.54f;
    }

    /** ART's display density; arc scales the whole mobile UI by it. */
    @BridgeOverride
    public float getDensity() {
        return Bridge.options.density;
    }

    /** Never called: a bridge that only draws into the activity's surface has no window. No {@code @BridgeOverride} on
     *  purpose - arc declares it abstract up to v156 and removes it in v157. */
    @SuppressWarnings("unused")
    @BridgeOverride
    public boolean setWindowedMode(int width, int height) {
        return false;
    }

    @BridgeOverride
    public void setTitle(String title) {
    }

    @BridgeOverride
    public void setVSync(boolean vsync) {
    }

    @BridgeOverride
    public BufferFormat getBufferFormat() {
        return bufferFormat;
    }

    @BridgeOverride
    public boolean supportsExtension(String extension) {
        if (extensions == null && gl20 != null)
            extensions = gl20.glGetString(GL20.GL_EXTENSIONS);
        return extensions != null && extensions.contains(extension);
    }

    @BridgeOverride
    public boolean isContinuousRendering() {
        return true;
    }

    @BridgeOverride
    public void setContinuousRendering(boolean isContinuous) {
    }

    @BridgeOverride
    public void requestRendering() {
    }

    @BridgeOverride
    public boolean isFullscreen() {
        return true;
    }

    @BridgeOverride
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot) {
        return null;
    }

    @BridgeOverride
    protected void setCursor(Cursor cursor) {
    }

    @BridgeOverride
    protected void setSystemCursor(SystemCursor systemCursor) {
    }

    /** The surface belongs to the host activity, so a resize request from the game has nothing to act on. */
    @BridgeOverride
    public void setResizable(boolean resizable) {
    }

    /** The window is the host activity's, not something the game decorates. */
    @BridgeOverride
    public void setUndecorated(boolean undecorated) {
    }

    @BridgeOverride
    public Monitor getPrimaryMonitor() {
        return null;
    }

    @BridgeOverride
    public Monitor getMonitor() {
        return null;
    }

    @BridgeOverride
    public Monitor[] getMonitors() {
        return new Monitor[0];
    }

    @BridgeOverride
    public DisplayMode[] getDisplayModes() {
        return new DisplayMode[0];
    }

    @BridgeOverride
    public DisplayMode[] getDisplayModes(Monitor monitor) {
        return new DisplayMode[0];
    }

    @BridgeOverride
    public DisplayMode getDisplayMode() {
        return null;
    }

    @BridgeOverride
    public DisplayMode getDisplayMode(Monitor monitor) {
        return null;
    }

    @BridgeOverride
    public boolean supportsDisplayModeChange() {
        return false;
    }

    @BridgeOverride
    public boolean setFullscreenMode(DisplayMode displayMode) {
        return false;
    }
}
