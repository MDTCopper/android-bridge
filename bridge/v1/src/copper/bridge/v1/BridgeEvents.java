package copper.bridge.v1;

import com.badlogic.gdx.*;

import copper.bridge.gen.JvmBus;
import copper.bridge.jvm.Events;
import copper.bridge.util.Log;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.core.GameState;

/**
 * The branch side of {@link Events}: the handlers, performed on the thread that owns the game. None of what ART
 * reports may be applied anywhere else - the surface pointer has to reach the thread that makes the EGL context
 * current, and the listener callbacks belong to that same thread.
 */
public class BridgeEvents implements Events {
    private final BridgeGraphics graphics;
    private final BridgeInput input;
    private final ApplicationListener listener;
    private final BridgeApplication application;

    private volatile boolean running = true;
    private volatile boolean paused;
    private boolean surfaceReady;

    public BridgeEvents(BridgeApplication application, BridgeGraphics graphics, BridgeInput input,
                        ApplicationListener listener) {
        this.application = application;
        this.graphics = graphics;
        this.input = input;
        this.listener = listener;
        JvmBus.bind(this);
    }

    public boolean running() {
        return running;
    }

    public boolean paused() {
        return paused;
    }

    /** Whether the render loop owns GL, which is not the same as an EGL surface being bound. */
    public boolean surfaceReady() {
        return surfaceReady;
    }

    public void markSurfaceReady() {
        surfaceReady = true;
    }

    @Override
    public void onSurfaceCreated(int width, int height, long window) {
        Surface.created(window, width, height);

        if (window == 0) {
            Log.error("ART reported a surface with a null window");
            return;
        }

        if (surfaceReady) {
            if (graphics.createSurface(window, width, height))
                listener.resize(width, height);
        }
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Surface.resized(width, height);

        if (!surfaceReady)
            return;

        graphics.surfaceResized(width, height);
        listener.resize(width, height);
    }

    @Override
    public void onSurfaceDestroyed() {
        Surface.destroyed();
        graphics.destroySurface();
        // surfaceReady deliberately stays true: it means "the render loop owns GL", not "an EGL surface is bound".
    }

    @Override
    public void onPause() {
        if (paused)
            return;
        paused = true;

        input.onPause();
        listener.pause();
        Log.info("paused");
    }

    @Override
    public void onResume() {
        if (!paused)
            return;
        paused = false;

        input.onResume();
        graphics.markResumed();
        listener.resume();
        Log.info("resumed");
    }

    @Override
    public void onDestroy() {
        running = false;
    }

    @Override
    public void onBackPressed() {
        // The old game has no exit button: on the main menu, back is the exit.
        if (onMenu()) {
            application.exit();
            return;
        }
        if (!input.backTap())
            application.exit();
    }

    private static boolean onMenu() {
        try {
            return Vars.state != null && Vars.state.is(GameState.State.menu);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
