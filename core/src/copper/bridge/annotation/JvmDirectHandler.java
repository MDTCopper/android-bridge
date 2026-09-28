package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A JVM-side handler of a direct call: ART calls it on the calling thread, with no queue in between.
 * Reserved: the game state is single threaded, so only a method indifferent to its thread may run this way,
 * and the ART main thread must never wait on the game loop.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvmDirectHandler {
}
