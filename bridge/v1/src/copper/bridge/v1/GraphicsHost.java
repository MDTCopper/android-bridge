package copper.bridge.v1;

/**
 * What the event handler needs from a branch's graphics. The branch classes are compiled against different arc
 * versions, so the handler takes this interface instead of a concrete type and the frames stay ordinary calls.
 */
public interface GraphicsHost {
    boolean createSurface(long window, int width, int height);

    void surfaceResized(int width, int height);

    void destroySurface();

    void markResumed();

    /** Only the newer branches carry safe-area insets; the older ones have nothing to apply. */
    default void safeInsetsUpdated(int top, int bottom, int left, int right) {
    }
}
