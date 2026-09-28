package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ART-side handler of an event the JVM side posts. Reserved: nothing is declared with it, kept because the
 * two sides crossed with the three channels is the generator's whole grid - a missing cell would cost a
 * mechanism change later, not just a declaration.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtEventHandler {
}
