package copper.bridge.jvm;

import copper.bridge.annotation.*;

/**
 * The events ART posts, implemented by the branch. Declared in core because the processor cannot see a branch.
 *
 * <p>Names follow Android's callbacks ({@code onXxx}); each one is a no-op default, so a branch that cannot serve an
 * event leaves it empty.</p>
 */
public interface Events {
    @JvmEventHandler
    default void onSurfaceCreated(int width, int height, long window) {
    }

    @JvmEventHandler
    default void onSurfaceChanged(int width, int height) {
    }

    @JvmEventHandler
    default void onSafeInsetsChanged(int top, int bottom, int left, int right) {
    }

    @JvmEventHandler
    default void onSurfaceDestroyed() {
    }

    @JvmEventHandler
    default void onPause() {
    }

    @JvmEventHandler
    default void onResume() {
    }

    @JvmEventHandler
    default void onDestroy() {
    }

    @JvmEventHandler
    default void onBackPressed() {
    }

    /** A music handle reached the end of its track; the audio path turns that into its listener's call. */
    @JvmEventHandler
    default void onMusicComplete(int music) {
    }
}
