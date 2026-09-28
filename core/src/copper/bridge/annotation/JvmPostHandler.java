package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A JVM-side handler of a posted call: ART queues it and the game loop performs it. Reserved for now; it runs
 * on the game thread, the only thread allowed to touch the game state, so its cost is the frame budget.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvmPostHandler {
    String[] callbacks() default {};
}
