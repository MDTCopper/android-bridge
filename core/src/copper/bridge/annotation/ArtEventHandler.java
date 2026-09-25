package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ART-side handler of an event the JVM side posts.
 *
 * <p>Reserved: nothing is declared with it yet. The six handler annotations are the two sides
 * crossed with the three channels; leaving one out would mean the generator knew only part of that
 * grid, so a later declaration would need the mechanism changed as well as written.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtEventHandler {
}
