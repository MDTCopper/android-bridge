package copper.bridge.v1;

import arc.*;
import arc.struct.*;

import copper.bridge.gen.*;
import copper.bridge.jvm.Events;
import copper.bridge.jvm.Surface;
import copper.bridge.util.*;

/**
 * The branch side of {@link Events}: the handlers, performed on the thread that owns the game. None of what ART
 * reports may be applied anywhere else - the surface pointer has to reach the thread that makes the EGL context
 * current, and the listener callbacks belong to that same thread.
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

    public boolean running() {
        return running;
    }

    public boolean paused() {
        return paused;
    }

    /** Whether the render loop owns GL, which is not the same as an EGL surface being bound: the flag stays set across
     *  a surface ART took away, so the next one is wired into the same context instead of leaving the screen black. */
    public boolean surfaceReady() {
        return surfaceReady;
    }

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

        // Only the surface arriving before the loop starts separates itself here: the loop wires that one up itself.
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
    public void safeInsetsUpdated(int top, int bottom, int left, int right) {
        graphics.safeInsetsUpdated(top, bottom, left, right);
    }

    @Override
    public void surfaceDestroyed() {
        Surface.destroyed();
        graphics.destroySurface();
        // surfaceReady deliberately stays true: it means "the render loop owns GL", not "an EGL surface is bound".
        // Clearing it here stopped the next surface from being wired in, leaving the screen black after a pause.
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

    /** Routes a back press the way the game expects it. With a dialog open the scene gets the key directly - a plain
     *  key event let the dialog close and {@code Control} then saw "no dialog" in the same frame and hid the app.
     *  With no dialog open the press goes through the normal input path. */
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
