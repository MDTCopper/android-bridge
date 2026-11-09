package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a branch method that mirrors a method of a game supertype. The checker verifies the supertype still declares
 * it at one end of the branch's range; a method the hierarchy never had is dead compatibility code.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeInterface {
}
