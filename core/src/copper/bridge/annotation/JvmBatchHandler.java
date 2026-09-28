package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One Batch channel written by the JVM side and received on ART. Reserved: a Batch channel has no wake-up, so
 * the receiver must be the side already turning every frame - the JVM here - and this direction has no use
 * today;
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface JvmBatchHandler {
    /** True when a batch may be written and submitted from more than one thread. */
    boolean withLock() default false;

    int maxBatches();

    int maxBytes();
}
