package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a generated call that only tells the peer something: no handler runs in the caller's frame and
 * nothing comes back. Answers travel under this label too, since they share the event queue and dispatch.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Event {
}
