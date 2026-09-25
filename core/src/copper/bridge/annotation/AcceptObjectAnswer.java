package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Silences the generator's warning about a posted handler that returns an object.
 *
 * <p>Such an answer is built on the peer's main thread and handed over as a global reference, and
 * building it can wait on a collection of the caller's heap. Legal only for a rare call whose
 * caller is already blocked; this annotation is how a declaration says so on purpose.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface AcceptObjectAnswer {
}
