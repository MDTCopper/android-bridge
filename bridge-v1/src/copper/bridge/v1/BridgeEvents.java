package copper.bridge.v1;

import arc.*;
import arc.struct.*;

import copper.bridge.gen.*;
import copper.bridge.jvm.Events;
import copper.bridge.jvm.Surface;
import copper.bridge.util.*;

/**
 * The branch side of {@link Events}: the handlers, performed on the thread that owns the game.
 *
 * <p>ART reports what happens to the activity - a surface appearing or going away, a pause, a resume, a back
 * press - and none of it may be applied anywhere but the game loop: the surface pointer has to reach the thread
 * that makes the EGL context current, and the listener callbacks belong to the same thread as everything else the
 * game touches.</p>
 */
public class BridgeEvents implements Events {
    private final BridgeGraphics graphics;
    private final BridgeInput input;
    private final Seq<ApplicationListener> listeners;

    private volatile boolean running = true;
    private volatile boolean paused;
    private boolean surfaceReady;

    public BridgeEvents(BridgeGraphics graphics, BridgeInput input,
                        Seq<ApplicationListener> listeners) {
        this.graphics = graphics;
        this.input = input;
        this.listeners = listeners;
        JvmBus.bind(this);
    }

    /** Whether the game loop should keep turning. */
    public boolean running() {
        return running;
    }

    /** Whether the activity is in the background. */
    public boolean paused() {
        return paused;
    }

    /**
     * Whether the render loop owns GL.
     *
     * <p>That is not the same as an EGL surface being bound: the flag stays set across a surface
     * that ART took away, so the next window surface is wired into the same context instead of
     * leaving the screen black.</p>
     */
    public boolean surfaceReady() {
        return surfaceReady;
    }

    /** Records that the loop owns GL. Called once the context is current. */
    public void markSurfaceReady() {
        surfaceReady = true;
    }

    @Override
    public void surfaceCreated(int width, int height, long window) {
        Surface.created(window, width, height);

        if (window == 0) {
            Log.error("ART reported a surface with a null window");
            return;
        }

        // The surface that arrives before the loop starts is the one the loop wires up itself, and
        // that is all this flag separates. Anything after that replaces a window surface ART
        // destroyed, and the context is deliberately kept across it.
        if (surfaceReady) {
            if (graphics.createSurface(window, width, height)) {
                for (ApplicationListener listener : listeners)
                    listener.resize(width, height);
            }
        }
    }

    @Override
    public void surfaceResized(int width, int height) {
        Surface.resized(width, height);

        if (!surfaceReady)
            return;

        graphics.surfaceResized(width, height);
        for (ApplicationListener listener : listeners)
            listener.resize(width, height);
    }

    @Override
    public void surfaceDestroyed() {
        Surface.destroyed();
        graphics.destroySurface();
        // surfaceReady deliberately stays true: it means "the render loop owns GL", not "an EGL
        // surface is bound". Clearing it here stopped the next surface from ever being wired in, so
        // the screen stayed black for the rest of the session after any pause that took the surface
        // away (a file chooser, for one).
    }

    @Override
    public void pause() {
        if (paused)
            return;
        paused = true;

        // input state has to be dropped, or a key held across the pause stays down forever
        input.onPause();
        for (ApplicationListener listener : listeners)
            listener.pause();
        Log.info("paused");
    }

    @Override
    public void resume() {
        if (!paused)
            return;
        paused = false;

        input.onResume();
        graphics.markResumed();
        for (ApplicationListener listener : listeners)
            listener.resume();
        Log.info("resumed");
    }

    @Override
    public void destroy() {
        running = false;
    }

    /**
     * Routes a back press the way the game expects it. With a dialog open the scene gets the key directly:
     * that is the multiplexer's own routing minus the keyboard device's per-frame state, which is what
     * {@code Control} reads to decide whether to leave - a plain key event let the dialog close and then
     * {@code Control} saw "no dialog" in the very same frame and hid the whole app. With no dialog open the
     * press goes through the normal input path, so state menus, the pause menu and leaving the app all behave
     * as they do on a device.
     */
    @Override
    public void back() {
        if (Core.scene != null && Core.scene.hasDialog()) {
            Core.scene.keyDown(arc.input.KeyCode.back);
            Core.scene.keyUp(arc.input.KeyCode.back);
            return;
        }

        input.backTap();
    }
}
