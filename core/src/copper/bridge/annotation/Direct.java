package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Labels a generated call that runs on the calling thread and returns a value. Written by the generator, never
 * by hand.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Direct {
}
