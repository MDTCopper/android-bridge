package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ART-side handler of a posted call: the JVM side queues it and the ART main thread performs it.
 *
 * <p>The method name is the bus name and the name the generator uses for the kind constant. A non-{@code void}
 * return type makes the call synchronous - the caller blocks until this handler answers - so a handler that
 * starts an activity or does slow I/O must return nothing. {@link #callbacks()} is empty for a call that answers
 * by returning, and non-empty only alongside a leading {@code long request} parameter, the id the handler passes
 * back.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtPostHandler {
    /** Outcomes of an asynchronous call, each written as {@code name(type)}, at most one parameter. */
    String[] callbacks() default {};
}
