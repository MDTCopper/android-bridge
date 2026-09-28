package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ART-side handler of a direct call: it runs on the calling thread, in the caller's frame, with no queue
 * and no wake-up. Safe only for a method that neither touches a view nor starts an activity - a thread ART
 * has never seen has no Looper.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtDirectHandler {
}
