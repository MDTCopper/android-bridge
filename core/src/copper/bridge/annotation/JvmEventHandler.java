package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A JVM-side handler of an event ART posts: a notification with no answer, performed on the thread that owns
 * the game state. Declared in core though implemented in a version branch, so a missing method is a compile
 * error instead of a silently dropped notification.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvmEventHandler {
}
