package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a branch method as an override of a game supertype. Branch classes use this instead of {@code @Override} for
 * game methods; {@code @Override} is reserved for the bridge's own interfaces. The checker requires it on every game
 * override and rejects it where no game supertype declares the method at either end.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeOverride {
}
