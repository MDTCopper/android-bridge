package copper.bridge.jvm;

import copper.bridge.annotation.*;

/**
 * The events ART posts, declared here and implemented by the branch. The annotation processor runs in core
 * and cannot see a version branch, so this is the only place the declaration can live.
 */
public interface Events {
    @JvmEventHandler
    default void surfaceCreated(int width, int height, long window) {
    }

    @JvmEventHandler
    default void surfaceResized(int width, int height) {
    }

    @JvmEventHandler
    default void safeInsetsUpdated(int top, int bottom, int left, int right) {
    }

    @JvmEventHandler
    default void surfaceDestroyed() {
    }

    @JvmEventHandler
    default void pause() {
    }

    @JvmEventHandler
    default void resume() {
    }

    @JvmEventHandler
    default void destroy() {
    }

    @JvmEventHandler
    default void back() {
    }
}
