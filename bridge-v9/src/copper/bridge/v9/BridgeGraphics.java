package copper.bridge.v9;

import arc.*;
import arc.graphics.Cursor;
import arc.graphics.Cursor.SystemCursor;
import arc.graphics.*;
import arc.graphics.gl.*;

import copper.bridge.*;
import copper.bridge.BridgeOptions.GLMode;
import copper.bridge.backend.display.DisplayEngine;
import copper.bridge.jvm.*;
import copper.bridge.util.*;

/**
 * arc's graphics for the JVM side: the arc-shaped adapter over the backend's EGL/GLES display. v9's arc has one
 * {@code GLProvider} entry point instead of GL20/GL30, so the adapter installs that provider while the display owns
 * the context and surface.
 */
public class BridgeGraphics extends Graphics implements copper.bridge.v1.GraphicsHost {
    private final DisplayEngine display = new DisplayEngine();

    private GLProvider gl;
    private GLVersion glVersion;
    private BufferFormat bufferFormat = new BufferFormat(8, 8, 8, 0, 16, 0, 0, false);

    private int safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom;

    public BridgeGraphics() {
    }

    public void configure() {
        // v9's arc has one GL entry point wrapping ES3, so an ES2 context cannot run the game at all: a caller that
        // asked for one is told here, before anything is loaded, instead of failing on the first ES3 call later.
        if (Bridge.options.glMode == GLMode.GL2)
            throw new IllegalStateException("Mindustry 9 needs OpenGL ES 3; an OpenGL ES 2 launch was requested");

        display.configure(true);
    }

    /** Creates the context and window surface for the window ART handed over, or replaces only the surface. */
    public boolean createSurface(long window, int surfaceWidth, int surfaceHeight) {
        if (!display.createSurface(window, surfaceWidth, surfaceHeight))
            return false;

        if (gl == null)
            setupGL();

        return true;
    }

    /** v9 has one GL entry point: Core.glProvider, dispatched by the static Gl facade. */
    private void setupGL() {
        gl = new BridgeGLProvider();
        Core.glProvider = gl;

        String version = display.glVersion();
        String vendor = display.glVendor();
        String renderer = display.glRenderer();
        glVersion = new GLVersion(Application.ApplicationType.android, version, vendor, renderer);

        int[] fb = display.framebuffer();
        bufferFormat = new BufferFormat(fb[0], fb[1], fb[2], fb[3], fb[4], fb[5], fb[6], fb[7] != 0);

        Gl.reset();
        Log.info("GL", "renderer: " + renderer);
        Log.info("GL", "vendor: " + vendor);
        Log.info("GL", "version: " + version);
        Log.info("GL", "using OpenGL ES " + glVersion.majorVersion + " context"
                + (Bridge.options.glMode == GLMode.Auto ? "" : " (requested " + Bridge.options.glMode + ")"));
        // the context is created as ES3 above, but a driver that quietly handed out ES2 must not reach the game
        if (!display.supportsES3())
            throw new IllegalStateException("Mindustry 9 needs an OpenGL ES 3 context, but this one is " + version);
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

    public void safeInsetsUpdated(int top, int bottom, int left, int right) {
        safeInsetTop = top;
        safeInsetBottom = bottom;
        safeInsetLeft = left;
        safeInsetRight = right;
    }

    public void beginFrame() {
        display.beginFrame();
    }

    public void swapBuffers() {
        display.swapBuffers();
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
    public int[] getSafeInsets() {
        return new int[]{safeInsetLeft, safeInsetRight, safeInsetTop, safeInsetBottom};
    }

    @Override
    public long getFrameId() {
        return display.frameId();
    }

    @Override
    public float getDeltaTime() {
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
}
