package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * One Batch channel written by ART and received on the JVM side: a buffer per frame instead of a bus row per event.
 *
 * <pre>{@code
 * @ArtBatchHandler(maxBatches = 64, maxBytes = 1 << 20)     // JvmBatch.bind(instance) on the receiving side
 * public abstract class InputBatch {
 *     public int pointer;                                   // header field
 *
 *     public abstract void touch(int x, int y);             // one record kind
 * }
 * }</pre>
 *
 * <p>No wake-up: the receiver drains where it already turns frames, which is why the JVM side receives. Declared on
 * an abstract class named {@code <Something>Batch} - the generated accessor drops the suffix - with one abstract
 * method per record kind (at most 255) and header fields neither {@code final} nor {@code private}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ArtBatchHandler {
    /** True when a frame may be written from more than one thread; the writer then locks, and frame order is whatever
     *  the lock granted. */
    boolean withLock() default false;

    /** Frames that may wait unread before the writer drops instead of growing the backlog. */
    int maxBatches() default Integer.MAX_VALUE;

    /** Bytes those unread frames may hold in total; the byte bound of the same backlog. */
    int maxBytes() default Integer.MAX_VALUE;
}
