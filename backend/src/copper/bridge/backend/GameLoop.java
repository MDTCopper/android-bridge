package copper.bridge.backend;

import copper.bridge.gen.JvmBus;
import copper.bridge.gen.JvmCall;
import copper.bridge.jvm.Surface;
import copper.bridge.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame loop shared by every branch. The branch supplies the frame body and the two state queries that depend on
 * its arc events; the startup wait, posted queue, pause/idle gate and first-frame handshake live here.
 */
public final class GameLoop {
    public static final long SURFACE_TIMEOUT_MILLIS = 20000;
    public interface Host {
        boolean running();

        /** False while paused or without a current surface; the loop then idles and pumps events. */
        boolean canDraw();

        /** One frame: begin, update, draw, swap. */
        void frame();
    }

    private final List<Runnable> posted = new ArrayList<>();
    private final List<Runnable> executing = new ArrayList<>();

    /** Queues work for the game-loop thread; safe from any thread. */
    public void post(Runnable runnable) {
        synchronized (posted) {
            posted.add(runnable);
        }
    }

    /**
     * Waits for ART to hand over the native window, taking events while waiting: ART creates the surface once its
     * main thread is free again, so the window pointer arrives as one of those events.
     */
    public boolean waitForWindow() {
        return waitForWindow(SURFACE_TIMEOUT_MILLIS);
    }

    private boolean waitForWindow(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!Surface.ready()) {
            JvmBus.pump();
            if (Surface.ready())
                return true;
            if (System.currentTimeMillis() >= deadline)
                return false;
            sleep(2);
        }
        return true;
    }

    public void run(Host host) {
        boolean firstFrame = false;
        while (host.running()) {
            JvmBus.pump();
            drainPosted();

            if (!host.canDraw()) {
                // nothing may be drawn without a current surface, but events must keep flowing
                sleep(4);
                continue;
            }

            host.frame();

            if (!firstFrame) {
                firstFrame = true;
                JvmCall.onFirstFrameDrawn();
            }
        }
    }

    private void drainPosted() {
        synchronized (posted) {
            executing.clear();
            executing.addAll(posted);
            posted.clear();
        }
        for (Runnable runnable : executing) {
            try {
                runnable.run();
            } catch (Throwable t) {
                Log.error(t);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
