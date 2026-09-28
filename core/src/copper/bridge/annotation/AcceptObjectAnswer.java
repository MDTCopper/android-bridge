package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Silences the generator's warning about a posted handler that returns an object, which is legal only for a
 * rare call whose caller is already blocked: building that answer on the peer's main thread can wait on a
 * collection of the caller's heap.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface AcceptObjectAnswer {
}
