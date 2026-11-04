package copper.bridge.v2;

import arc.*;
import arc.graphics.Cursor;
import arc.graphics.Cursor.SystemCursor;
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
import java.util.function.Function;

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

    private GLProvider gl;
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
     * Points LWJGL at this device's EGL and GLES libraries and loads them: LWJGL would otherwise load its own bundled
     * natives, which do not exist for Android. A pair that is not there is reported and the system's used instead.
     *
     * <p>LWJGL's own natives come first: they sit inside the bridge jar, and every loader in the process has to be
     * pointed at the unpacked folder before the first library is opened.
     */
    public void configure() {
        // v9's arc has one GL entry point wrapping ES3, so an ES2 context cannot run the game at all: a caller that
        // asked for one is told here, before anything is loaded, instead of failing on the first ES3 call later.
        if (Bridge.options.glMode == GLMode.GL2)
            throw new IllegalStateException("Mindustry 9 needs OpenGL ES 3; an OpenGL ES 2 launch was requested");

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
     *  {@code java.library.path} for arc's {@code SharedLibraryLoader}, which would otherwise extract v9's game jar's
     *  Linux copy. */
    private void prepareLwjglNatives() {
        File lwjgl = LwjglNatives.extract();
        if (lwjgl == null)
            return;

        String libPath = lwjgl.getAbsolutePath();
        Configuration.LIBRARY_PATH.set(libPath);
        System.setProperty("java.library.path", libPath + File.pathSeparator + System.getProperty("java.library.path", ""));

        // LWJGL looks a bundled native up at linux/<arch>/<module>/<file>; the bridge jar keeps the same tail under
        // native/lwjgl/, and LwjglNatives::mapPath is the one place that layout is written down. LWJGL is then told
        // to extract into the folder just unpacked, where the bytes are the same and its CRC check matches: nothing
        // is written again.
        // LWJGL captures the mapper when Platform initializes, so it must be set here, before the first GL call.
        Configuration.BUNDLED_LIBRARY_PATH_MAPPER.set((Function<String, String>) LwjglNatives::mapPath);
        Configuration.SHARED_LIBRARY_EXTRACT_PATH.set(libPath);
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

        if (gl == null)
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

        // v9's arc has one GL entry point that wraps the ES3 functions, so an ES2 context would fail on the
        // first ES3 call instead of at creation - and a --gl2 request was refused in configure() already.
        eglContext = createContext(config, 3);
        if (eglContext == EGL10.EGL_NO_CONTEXT)
            throw new IllegalStateException("eglCreateContext failed: 0x"
                    + Integer.toHexString(EGL10.eglGetError()) + "; Mindustry 9 needs an OpenGL ES 3 context");

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

    /** v9 has one GL entry point: Core.glProvider, dispatched by the static Gl facade. */
    private void setupGL() {
        GLES.createCapabilities();

        gl = new BridgeGLProvider();
        Core.glProvider = gl;

        String version = gl.glGetString(Gl.version);
        String vendor = gl.glGetString(Gl.vendor);
        String renderer = gl.glGetString(Gl.renderer);
        glVersion = new GLVersion(Application.ApplicationType.android, version, vendor, renderer);

        Gl.reset();
        Log.info("GL", "renderer: " + renderer);
        Log.info("GL", "vendor: " + vendor);
        Log.info("GL", "version: " + version);
        Log.info("GL", "using OpenGL ES " + glVersion.majorVersion + " context"
                + (Bridge.options.glMode == GLMode.Auto ? "" : " (requested " + Bridge.options.glMode + ")"));
        // the context is created as ES3 above, but a driver that quietly handed out ES2 must not reach the game
        if (!glVersion.atLeast(3, 0))
            throw new IllegalStateException("Mindustry 9 needs an OpenGL ES 3 context, but this one is " + version);
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
        if (gl != null)
            GLES20.glViewport(0, 0, width, height);
    }

    /** Marks the next frame as the first after a resume: a pause can last minutes, and its time would otherwise reach
     *  the game as one delta and step its physics in one jump. */
    public void markResumed() {
        resumed = true;
    }

    public void safeInsetsUpdated(int top, int bottom, int left, int right) {
        safeInsetTop = top;
        safeInsetBottom = bottom;
        safeInsetLeft = left;
        safeInsetRight = right;
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

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getBackBufferWidth() {
        return width;
    }

    @Override
    public int getBackBufferHeight() {
        return height;
    }

    @Override
    public int[] getSafeInsets(){
        return new int[]{safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom};
    }

    @Override
    public long getFrameId() {
        return frameId;
    }

    @Override
    public float getDeltaTime() {
        return deltaTime;
    }

    @Override
    public int getFramesPerSecond() {
        return fps;
    }

    @Override
    public GLVersion getGLVersion() {
        return glVersion;
    }

    @Override
    public float getPpiX() {
        return Bridge.options.xdpi;
    }

    @Override
    public float getPpiY() {
        return Bridge.options.ydpi;
    }

    @Override
    public float getPpcX() {
        return Bridge.options.xdpi / 2.54f;
    }

    @Override
    public float getPpcY() {
        return Bridge.options.ydpi / 2.54f;
    }

    /** ART's display density; arc scales the whole mobile UI by it. */
    @Override
    public float getDensity() {
        return Bridge.options.density;
    }

    @Override
    public void setTitle(String title) {
    }

    @Override
    public void setVSync(boolean vsync) {
    }

    @Override
    public BufferFormat getBufferFormat() {
        return bufferFormat;
    }

    @Override
    public boolean supportsExtension(String extension) {
        if (extensions == null && gl != null)
            extensions = gl.glGetString(Gl.extensions);
        return extensions != null && extensions.contains(extension);
    }

    @Override
    public boolean isContinuousRendering() {
        return true;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous) {
    }

    @Override
    public void requestRendering() {
    }

    @Override
    public boolean isFullscreen() {
        return true;
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot) {
        return null;
    }

    @Override
    protected void setCursor(Cursor cursor) {
    }

    @Override
    protected void setSystemCursor(SystemCursor systemCursor) {
    }
}
