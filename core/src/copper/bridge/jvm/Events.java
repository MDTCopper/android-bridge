package copper.bridge.jvm;

import copper.bridge.annotation.*;

/**
 * The events ART posts, declared here and implemented by the branch. The annotation processor runs in core and
 * cannot see a version branch, so this is the only place the declaration can live; every method carries a no-op
 * default, so a version that cannot honour an event may not override it - at the cost of the compiler's count,
 * since a forgotten event is no longer a build failure - and Native still resolves each kind by name against the
 * bound instance, landing on that default. The generator reads the names, parameter names and types here.
 */
public interface Events {
    @JvmEventHandler
    default void surfaceCreated(int width, int height, long window) {
    }

    @JvmEventHandler
    default void surfaceResized(int width, int height) {
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
