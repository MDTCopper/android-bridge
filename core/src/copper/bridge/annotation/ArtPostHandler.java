package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ART-side handler of a posted call: the JVM side queues it and the ART main thread performs it. The
 * method name is the bus name and the kind constant's name. A non-{@code void} return makes the call
 * synchronous - the caller blocks until this handler answers - so a handler that starts an activity or does
 * slow I/O must return nothing.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtPostHandler {
    /** Outcomes of an asynchronous call, each written as {@code name(type)}, at most one parameter. Empty for
     * a call that answers by returning; non-empty only alongside a leading {@code long request} id. */
    String[] callbacks() default {};
}
