package copper.bridge.v6;

import arc.*;
import arc.Graphics.Cursor.*;
import arc.graphics.*;
import arc.graphics.gl.*;

import copper.bridge.*;
import copper.bridge.BridgeOptions.GLMode;
import copper.bridge.backend.display.DisplayEngine;
import copper.bridge.jvm.*;
import copper.bridge.util.*;

/**
 * arc's graphics for the JVM side: the arc-shaped adapter over the backend's EGL/GLES display. A pause destroys only
 * the window surface, so GL objects survive a resume. v7 and v8 reuse this class.
 */
public class BridgeGraphics extends Graphics implements copper.bridge.v1.GraphicsHost {
    private final DisplayEngine display = new DisplayEngine();

    private GL20 gl20;
    private GL30 gl30;
    private GLVersion glVersion;
    private BufferFormat bufferFormat = new BufferFormat(8, 8, 8, 0, 16, 0, 0, false);

    private int safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom;

    public BridgeGraphics() {
    }

    public void configure() {
        display.configure();
    }

    /** Creates the context and window surface for the window ART handed over, or replaces only the surface. */
    public boolean createSurface(long window, int surfaceWidth, int surfaceHeight) {
        if (!display.createSurface(window, surfaceWidth, surfaceHeight))
            return false;

        if (gl20 == null)
            setupGL();

        return true;
    }

    /** The ES3 wrapper must not be installed on a device that only offers ES2. */
    private void setupGL() {
        gl20 = new BridgeGL20();
        Core.gl = gl20;
        Core.gl20 = gl20;

        String version = display.glVersion();
        String vendor = display.glVendor();
        String renderer = display.glRenderer();
        glVersion = new GLVersion(Application.ApplicationType.values()[0], version, vendor, renderer);

        int[] fb = display.framebuffer();
        bufferFormat = new BufferFormat(fb[0], fb[1], fb[2], fb[3], fb[4], fb[5], fb[6], fb[7] != 0);

        if (Bridge.options.glMode == GLMode.GL3 && !display.supportsES3())
            throw new IllegalStateException("OpenGL ES 3 was requested, but the context is " + version);

        if (Bridge.options.glMode != GLMode.GL2 && display.supportsES3()) {
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

    /** {@code arc.graphics.Gl} does not exist at the range's floor; there is no static batch state to reset there. */
    private static void resetGl() {
        try {
            Class.forName("arc.graphics.Gl").getMethod("reset").invoke(null);
        } catch (Throwable ignored) {
        }
    }

    public void destroySurface() {
        display.destroySurface();
    }

    public void disposeContext() {
        display.disposeContext();
    }

    public void surfaceResized(int surfaceWidth, int surfaceHeight) {
        display.surfaceResized(surfaceWidth, surfaceHeight);
    }

    public void markResumed() {
        display.markResumed();
    }

    public void beginFrame() {
        display.beginFrame();
    }

    public void swapBuffers() {
        display.swapBuffers();
    }

    @Override
    public GL20 getGL20() {
        return gl20;
    }

    /** Abstract on the older end of the range, concrete on the newer one; the same answer either way. */
    @Override
    public boolean isGL30Available() {
        return gl30 != null;
    }

    @Override
    public void setGL20(GL20 gl20) {
        this.gl20 = gl20;
        if (gl30 == null) {
            Core.gl = gl20;
            Core.gl20 = gl20;
        }
    }

    @Override
    public GL30 getGL30() {
        return gl30;
    }

    @Override
    public void setGL30(GL30 gl30) {
        this.gl30 = gl30;
        if (gl30 != null) {
            this.gl20 = gl30;
            Core.gl = gl30;
            Core.gl20 = gl30;
            Core.gl30 = gl30;
        }
    }

    @Override
    public int getWidth() {
        return display.width();
    }

    @Override
    public int getHeight() {
        return display.height();
    }

    @Override
    public int getBackBufferWidth() {
        return display.width();
    }

    @Override
    public int getBackBufferHeight() {
        return display.height();
    }

    @Override
    public long getFrameId() {
        return display.frameId();
    }

    @Override
    public float getDeltaTime() {
        return display.deltaTime();
    }

    /** Abstract on the older end of the range, removed before its newer one: no {@code @Override} on purpose. */
    @SuppressWarnings("unused")
    public float getRawDeltaTime() {
        return display.deltaTime();
    }

    @Override
    public int getFramesPerSecond() {
        return display.framesPerSecond();
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

    /** Never called: a bridge that only draws into the activity's surface has no window. No {@code @Override} on
     *  purpose - arc declares it abstract up to v156 and removes it in v157. */
    @SuppressWarnings("unused")
    public boolean setWindowedMode(int width, int height) {
        return false;
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
        return display.supportsExtension(extension);
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

    /** The surface belongs to the host activity, so a resize request from the game has nothing to act on. */
    @Override
    public void setResizable(boolean resizable) {
    }

    /** The window is the host activity's, not something the game decorates. */
    @Override
    public void setUndecorated(boolean undecorated) {
    }

    @Override
    public Monitor getPrimaryMonitor() {
        return null;
    }

    @Override
    public Monitor getMonitor() {
        return null;
    }

    @Override
    public Monitor[] getMonitors() {
        return new Monitor[0];
    }

    @Override
    public DisplayMode[] getDisplayModes() {
        return new DisplayMode[0];
    }

    @Override
    public DisplayMode[] getDisplayModes(Monitor monitor) {
        return new DisplayMode[0];
    }

    @Override
    public DisplayMode getDisplayMode() {
        return null;
    }

    @Override
    public DisplayMode getDisplayMode(Monitor monitor) {
        return null;
    }

    @Override
    public boolean supportsDisplayModeChange() {
        return false;
    }

    @Override
    public boolean setFullscreenMode(DisplayMode displayMode) {
        return false;
    }

    /** v8 asks its Graphics for this; v6 has neither a caller nor the interface method, so no {@code @Override}. */
    public void safeInsetsUpdated(int top, int bottom, int left, int right) {
        safeInsetTop = top;
        safeInsetBottom = bottom;
        safeInsetLeft = left;
        safeInsetRight = right;
    }

    /** @see #safeInsetsUpdated; no {@code @Override} for the same reason. */
    public int[] getSafeInsets() {
        return new int[]{safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom};
    }
}
