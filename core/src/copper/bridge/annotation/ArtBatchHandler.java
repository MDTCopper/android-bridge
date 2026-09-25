package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One Batch channel whose records are written by ART and received on the JVM side. The annotated
 * class is the schema: header fields, and one abstract method per record kind, implemented by a
 * branch - which is what makes the schema visible to the generator without looking outside core.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ArtBatchHandler {
    /** True when a batch may be written and submitted from more than one thread. */
    boolean withLock() default false;

    /** Hard bound on queued batches; the producer drops the newest one and counts it when full. */
    int maxBatches() default Integer.MAX_VALUE;

    /** Hard bound on queued bytes, with the same policy. */
    int maxBytes() default Integer.MAX_VALUE;
}
