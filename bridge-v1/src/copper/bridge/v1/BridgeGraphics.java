package copper.bridge.v1;

import arc.*;
import arc.Graphics.Cursor.*;
import arc.graphics.*;
import arc.graphics.gl.*;

import copper.bridge.*;
import copper.bridge.util.*;
import org.lwjgl.*;
import org.lwjgl.egl.*;
import org.lwjgl.opengles.*;
import org.lwjgl.system.*;

import java.io.*;
import java.nio.*;

/**
 * arc's graphics for the JVM side of the bridge.
 *
 * <p>The game runs in a separate JVM, where {@code android.opengl} is unreachable, so LWJGL's EGL
 * and OpenGL ES bindings are the only route to GL. ART hands the window over as an opaque native
 * pointer, which is exactly what {@code eglCreateWindowSurface} wants. A pause destroys only the
 * window surface, so the context and its textures and shaders survive a resume.</p>
 */
public class BridgeGraphics extends Graphics{
    /** Coverage sampling attribute; not in LWJGL's core EGL headers. */
    private static final int EGL_COVERAGE_SAMPLES_NV = 0x30E1;

    /** The names ANGLE's libraries have. */
    private static final String ANGLE_EGL = "libEGL_angle.so";
    private static final String ANGLE_GLES = "libGLESv2_angle.so";

    /** The system's own EGL and GLES, used whenever ANGLE is not requested or cannot be had. */
    private static final String SYSTEM_EGL = "libEGL.so";
    private static final String SYSTEM_GLES = "libGLESv2.so";

    /**
     * Where a device's own ANGLE may sit, in the order the search tries them: the platform's
     * libraries, the ANGLE apex, then the partitions a vendor build may put it in. A 64-bit process
     * reads the {@code lib64} form of each.
     */
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

    private long frameId = -1;
    private long lastFrameTime = -1;
    private long frameCounterStart = System.nanoTime();
    private int frames;
    private int fps;
    private float deltaTime;

    /** Set when the context was made current again, so the first delta of a resume is not huge. */
    private boolean resumed;

    private BufferFormat bufferFormat = new BufferFormat(8, 8, 8, 0, 16, 0, 0, false);

    public BridgeGraphics(){
    }

    /**
     * Points LWJGL at this device's EGL and GLES libraries and loads them. LWJGL would otherwise
     * load its own bundled natives, which do not exist for Android, and its OpenGL ES binding needs
     * an explicit create rather than the lazy one.
     *
     * <p>ANGLE is asked for in two forms: {@code --angle} takes the device's own pair, found under
     * the system's library directories, and {@code --angle-path} takes a pair the caller supplied,
     * found under that folder. A pair that is not there is reported and the system's EGL/GLES are
     * used instead, so asking for ANGLE never costs a launch.</p>
     *
     * <p>A pair from {@code --angle-path} is made executable for the owner and read-only before LWJGL gets
     * it, the same W^X treatment the bridge's own libraries get. The device's own pair is not touched.</p>
     */
    public void configure(){
        String egl = SYSTEM_EGL;
        String gles = SYSTEM_GLES;

        if(Bridge.options.angle){
            if(Bridge.options.anglePath != null){
                File eglAngle = findLibrary(Bridge.options.anglePath, Bridge.options.abi, ANGLE_EGL, SYSTEM_EGL);
                File glesAngle = findLibrary(Bridge.options.anglePath, Bridge.options.abi, ANGLE_GLES, SYSTEM_GLES);
                if(eglAngle != null && glesAngle != null){
                    // W^X: LWJGL opens these by absolute path, so they have to be executable and not
                    // writable. A pair from --angle-path is the only one the bridge may touch.
                    boolean success = true;
                    success &= eglAngle.setExecutable(true, true);
                    success &= eglAngle.setReadOnly();
                    success &= glesAngle.setExecutable(true, true);
                    success &= glesAngle.setReadOnly();
                    if (!success)
                        Log.warn("failed to mark angle executable and readonly");

                    egl = eglAngle.getAbsolutePath();
                    gles = glesAngle.getAbsolutePath();
                }else{
                    Log.warn("GL", "ANGLE was requested but no EGL/GLES pair was found under "
                            + Bridge.options.anglePath + "; using the system libraries");
                }
            }else{
                String[] device = findSystemAngle();
                if(device != null){
                    egl = device[0];
                    gles = device[1];
                }else{
                    Log.warn("GL", "ANGLE was requested but this device has no " + ANGLE_EGL
                            + "/" + ANGLE_GLES + "; using the system libraries");
                }
            }
        }

        Configuration.DISABLE_HASH_CHECKS.set(true);
        Configuration.EGL_LIBRARY_NAME.set(egl);
        Configuration.OPENGLES_LIBRARY_NAME.set(gles);
        Configuration.OPENGLES_EXPLICIT_INIT.set(true);
        if(Bridge.options.debug || Bridge.options.verbose)
            Configuration.DEBUG.set(true);

        Log.info("GL", "EGL library: " + egl);
        Log.info("GL", "GLES library: " + gles);
        GLES.create();
    }

    /** Looks in the ABI subfolder first, then flat; the caller may pass either layout. */
    private static File findLibrary(File root, String abi, String... names){
        for(String name : names){
            if(abi != null && !abi.isEmpty()){
                File byAbi = new File(new File(root, abi), name);
                if(byAbi.isFile())
                    return byAbi;
            }
            File flat = new File(root, name);
            if(flat.isFile())
                return flat;
        }
        return null;
    }

    /**
     * Finds the device's own ANGLE pair: the system's library directories are searched in turn and the
     * first one holding both libraries wins. Only their presence is settled here - whether the linker
     * accepts the directory is what the load LWJGL performs a moment later says.
     *
     * @return the two absolute paths, EGL first, or {@code null} when no directory holds a pair
     */
    private static String[] findSystemAngle(){
        String suffix = bits64() ? "lib64" : "lib";
        for(String root : SYSTEM_LIBRARY_ROOTS){
            File dir = new File(root + "/" + suffix);
            File egl = new File(dir, ANGLE_EGL);
            File gles = new File(dir, ANGLE_GLES);
            if(egl.isFile() && gles.isFile())
                return new String[]{egl.getAbsolutePath(), gles.getAbsolutePath()};
        }
        return null;
    }

    /** Whether this process is 64 bit, which decides between the {@code lib} and {@code lib64} forms. */
    private static boolean bits64(){
        String arch = Bridge.options.arch == null ? "" : Bridge.options.arch;
        String abi = Bridge.options.abi == null ? "" : Bridge.options.abi;
        return arch.contains("64") || arch.startsWith("armv8") || abi.contains("64");
    }

    /**
     * Creates the context and window surface for a window ART handed over, or replaces only the
     * surface when that is all that was lost. {@code window} is the {@code ANativeWindow*} pointer.
     */
    public boolean createSurface(long window, int surfaceWidth, int surfaceHeight){
        if(eglDisplay == EGL10.EGL_NO_DISPLAY || eglContext == EGL10.EGL_NO_CONTEXT){
            fullInit(window);
        }else{
            createWindowSurface(window);
        }

        if(!EGL10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)){
            Log.error("GL", "eglMakeCurrent failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
            return false;
        }

        width = surfaceWidth;
        height = surfaceHeight;

        if(gl20 == null)
            setupGL();

        GLES20.glViewport(0, 0, width, height);

        lastFrameTime = System.nanoTime();
        resumed = true;
        return true;
    }

    /** Creates the display, config and context; the window surface is made afterwards. */
    private void fullInit(long window){
        eglDisplay = EGL10.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(eglDisplay == EGL10.EGL_NO_DISPLAY)
            throw new IllegalStateException("eglGetDisplay failed");

        try(MemoryStack stack = MemoryStack.stackPush()){
            if(!EGL10.eglInitialize(eglDisplay, stack.mallocInt(1), stack.mallocInt(1)))
                throw new IllegalStateException("eglInitialize failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
        }

        long config = chooseConfig();
        logConfig(config);

        try(MemoryStack stack = MemoryStack.stackPush()){
            // ES2 is requested even for an ES3 context: every driver that can give an ES3 context
            // reports the ES2 bit, and asking for an ES3 bit is not portable.
            int[] attribs = {EGL13.EGL_CONTEXT_CLIENT_VERSION, Bridge.options.useGL30 ? 3 : 2, EGL10.EGL_NONE};
            eglContext = EGL10.eglCreateContext(eglDisplay, config, EGL10.EGL_NO_CONTEXT, stack.ints(attribs));
        }
        if(eglContext == EGL10.EGL_NO_CONTEXT)
            throw new IllegalStateException("eglCreateContext failed: 0x" + Integer.toHexString(EGL10.eglGetError()));

        createWindowSurface(window);
    }

    private void createWindowSurface(long window){
        long config = chooseConfig();
        try(MemoryStack stack = MemoryStack.stackPush()){
            eglSurface = EGL10.eglCreateWindowSurface(eglDisplay, config, window, stack.ints(EGL10.EGL_NONE));
        }
        if(eglSurface == EGL10.EGL_NO_SURFACE)
            throw new IllegalStateException("eglCreateWindowSurface failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
    }

    /** Picks a window-renderable ES2 config matching the requested channel sizes. */
    private long chooseConfig(){
        try(MemoryStack stack = MemoryStack.stackPush()){
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
            if(!EGL10.eglChooseConfig(eglDisplay, stack.ints(attribs), configs, count))
                throw new IllegalStateException("eglChooseConfig failed: 0x" + Integer.toHexString(EGL10.eglGetError()));
            if(count.get(0) <= 0)
                throw new IllegalStateException("no EGL config supports an OpenGL ES window");
            return configs.get(0);
        }
    }

    /** Records what the chosen config actually delivered, for the game's own GL log. */
    private void logConfig(long config){
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

    private int attrib(long config, int attribute){
        try(MemoryStack stack = MemoryStack.stackPush()){
            IntBuffer value = stack.mallocInt(1);
            return EGL10.eglGetConfigAttrib(eglDisplay, config, attribute, value) ? value.get(0) : 0;
        }
    }

    /**
     * Builds the GL wrappers once the context is current: the ES3 one must not be installed on a
     * device that only offers ES2, so the choice needs the live version string.
     */
    private void setupGL(){
        GLES.createCapabilities();

        gl20 = new BridgeGL20();
        Core.gl = gl20;
        Core.gl20 = gl20;

        String version = gl20.glGetString(GL20.GL_VERSION);
        String vendor = gl20.glGetString(GL20.GL_VENDOR);
        String renderer = gl20.glGetString(GL20.GL_RENDERER);
        glVersion = new GLVersion(Application.ApplicationType.android, version, vendor, renderer);

        if(Bridge.options.useGL30 && glVersion.atLeast(3, 0) && glVersion.majorVersion > 2){
            gl30 = new BridgeGL30();
            gl20 = gl30;
            Core.gl = gl30;
            Core.gl20 = gl30;
            Core.gl30 = gl30;
        }

        Gl.reset();
        Log.info("GL", "renderer: " + renderer);
        Log.info("GL", "vendor: " + vendor);
        Log.info("GL", "version: " + version);
        Log.info("GL", "using " + (gl30 != null ? "OpenGL ES 3" : "OpenGL ES 2") + " context");
    }

    /** Releases the window surface but keeps the context, so a resume does not rebuild its objects. */
    public void destroySurface(){
        if(eglDisplay == EGL10.EGL_NO_DISPLAY)
            return;

        EGL10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        if(eglSurface != EGL10.EGL_NO_SURFACE){
            EGL10.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL10.EGL_NO_SURFACE;
        }
    }

    /** Tears the context down for good; only the destroy path needs this. */
    public void disposeContext(){
        destroySurface();
        if(eglDisplay == EGL10.EGL_NO_DISPLAY)
            return;

        if(eglContext != EGL10.EGL_NO_CONTEXT){
            EGL10.eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL10.EGL_NO_CONTEXT;
        }
        EGL10.eglTerminate(eglDisplay);
        eglDisplay = EGL10.EGL_NO_DISPLAY;
    }

    /** Records the new surface size; the caller already resized the viewport if needed. */
    public void surfaceResized(int surfaceWidth, int surfaceHeight){
        width = surfaceWidth;
        height = surfaceHeight;
        if(gl20 != null)
            GLES20.glViewport(0, 0, width, height);
    }

    /**
     * Marks the next frame as the first after a resume: a pause can last minutes, and its time would
     * otherwise reach the game as one delta and step its physics in one jump.
     */
    public void markResumed(){
        resumed = true;
    }

    /** Advances the frame clock. Called once per loop iteration, before the game updates. */
    public void beginFrame(){
        long time = System.nanoTime();
        if(lastFrameTime == -1)
            lastFrameTime = time;

        deltaTime = (time - lastFrameTime) / 1000000000.0f;
        lastFrameTime = time;

        if(resumed){
            deltaTime = 0f;
            resumed = false;
        }
        if(deltaTime == 0f)
            deltaTime = 1f / 60f;

        if(time - frameCounterStart >= 1000000000L){
            fps = frames;
            frames = 0;
            frameCounterStart = time;
        }
        frames++;
        frameId++;
    }

    public void swapBuffers(){
        if(eglSurface != EGL10.EGL_NO_SURFACE)
            EGL10.eglSwapBuffers(eglDisplay, eglSurface);
    }

    @Override
    public GL20 getGL20(){
        return gl20;
    }

    @Override
    public void setGL20(GL20 gl20){
        this.gl20 = gl20;
        if(gl30 == null){
            Core.gl = gl20;
            Core.gl20 = gl20;
        }
    }

    @Override
    public GL30 getGL30(){
        return gl30;
    }

    @Override
    public void setGL30(GL30 gl30){
        this.gl30 = gl30;
        if(gl30 != null){
            this.gl20 = gl30;
            Core.gl = gl30;
            Core.gl20 = gl30;
            Core.gl30 = gl30;
        }
    }

    @Override
    public int getWidth(){
        return width;
    }

    @Override
    public int getHeight(){
        return height;
    }

    @Override
    public int getBackBufferWidth(){
        return width;
    }

    @Override
    public int getBackBufferHeight(){
        return height;
    }

    @Override
    public long getFrameId(){
        return frameId;
    }

    @Override
    public float getDeltaTime(){
        return deltaTime;
    }

    @Override
    public int getFramesPerSecond(){
        return fps;
    }

    @Override
    public GLVersion getGLVersion(){
        return glVersion;
    }

    @Override
    public float getPpiX(){
        return Bridge.options.xdpi;
    }

    @Override
    public float getPpiY(){
        return Bridge.options.ydpi;
    }

    @Override
    public float getPpcX(){
        return Bridge.options.xdpi / 2.54f;
    }

    @Override
    public float getPpcY(){
        return Bridge.options.ydpi / 2.54f;
    }

    /** ART's display density; arc scales the whole mobile UI by it, so it cannot be a constant. */
    @Override
    public float getDensity(){
        return Bridge.options.density;
    }

    /**
     * Never called: a bridge that only draws into the activity's surface has no window.
     *
     * <p>No {@code @Override} on purpose: arc declares it abstract up to v156 and removes it in
     * v157, so with one epoch compiled and all of them run, the annotation cannot be present and
     * the method cannot be dropped - v156 would be left with an unimplemented abstract method.</p>
     */
    public boolean setWindowedMode(int width, int height){
        return false;
    }

    @Override
    public void setTitle(String title){
    }

    @Override
    public void setVSync(boolean vsync){
    }

    @Override
    public BufferFormat getBufferFormat(){
        return bufferFormat;
    }

    @Override
    public boolean supportsExtension(String extension){
        if(extensions == null && gl20 != null)
            extensions = gl20.glGetString(GL20.GL_EXTENSIONS);
        return extensions != null && extensions.contains(extension);
    }

    @Override
    public boolean isContinuousRendering(){
        return true;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous){
    }

    /** The loop renders every frame, so there is never anything to wake up. */
    @Override
    public void requestRendering(){
    }

    @Override
    public boolean isFullscreen(){
        return true;
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot){
        return null;
    }

    @Override
    protected void setCursor(Cursor cursor){
    }

    @Override
    protected void setSystemCursor(SystemCursor systemCursor){
    }
}
