package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One Batch channel whose records are written by the JVM side and received on ART.
 *
 * <p>Reserved: a Batch channel has no wake-up, so the receiver must be the side already turning
 * every frame - the JVM here - and this direction has no use today. It exists so the generator's
 * two halves stay symmetric.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface JvmBatchHandler {
    /** True when a batch may be written and submitted from more than one thread. */
    boolean withLock() default false;

    /** Hard bound on queued batches; full means the newest is dropped and counted. */
    int maxBatches();

    /** Hard bound on queued bytes, same policy. */
    int maxBytes();
}
