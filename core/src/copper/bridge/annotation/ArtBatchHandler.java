package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One Batch channel written by ART and received on the JVM side.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ArtBatchHandler {
    /** True when a batch may be written and submitted from more than one thread. */
    boolean withLock() default false;

    int maxBatches() default Integer.MAX_VALUE;

    int maxBytes() default Integer.MAX_VALUE;
}
