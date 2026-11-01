package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * One Batch channel written by the JVM side and received on ART.
 *
 * <p>Reserved: nothing declares it today. A Batch channel has no wake-up, so its receiver must be the side already
 * turning every frame - the JVM, not ART - and the mirror {@link ArtBatchHandler} is the direction that works.</p>
 *
 * <pre>{@code
 * @JvmBatchHandler(maxBatches = 8, maxBytes = 1 << 16)       // ArtBatch.bind(instance) on the receiving side
 * public abstract class RenderBatch {
 *     public long frame;                                     // header field
 *
 *     public abstract void quad(float x, float y);            // one record kind
 * }
 * }</pre>
 *
 * <p>Same schema rules as {@link ArtBatchHandler}; both bounds are required here, with no defaults to fall back on.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface JvmBatchHandler {
    /** True when a frame may be written from more than one thread; the writer then locks, and frame order is whatever
     *  the lock granted. */
    boolean withLock() default false;

    /** Frames that may wait unread before the writer drops instead of growing the backlog. */
    int maxBatches() default Integer.MAX_VALUE;

    /** Bytes those unread frames may hold in total; the byte bound of the same backlog. */
    int maxBytes() default Integer.MAX_VALUE;
}
