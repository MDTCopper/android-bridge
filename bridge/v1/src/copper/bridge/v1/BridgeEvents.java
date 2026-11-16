package copper.bridge.v1;

import com.badlogic.gdx.*;
import com.badlogic.gdx.utils.*;

import copper.bridge.gen.JvmBus;
import copper.bridge.jvm.Events;
import copper.bridge.util.Log;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.core.GameState;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.scene.Element;
import io.anuke.ucore.scene.ui.Dialog;

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
        // A dialog owns back while it is open; only the bare main menu is the old game's exit path.
        if (hasDialog()) {
            input.backTap();
            return;
        }
        if (onMenu()) {
            application.exit();
            return;
        }
        // In play, back is the game's menu key; never turn an unhandled press into an exit.
        input.backTap();
    }

    private static boolean hasDialog() {
        try {
            if (Core.scene == null)
                return false;
            Array<Element> children = Core.scene.getRoot().getChildren();
            for (int i = 0; i < children.size; i++) {
                Element child = children.get(i);
                if (child instanceof Dialog && ((Dialog)child).isShown())
                    return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean onMenu() {
        try {
            return Vars.state != null && Vars.state.is(GameState.State.menu);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
