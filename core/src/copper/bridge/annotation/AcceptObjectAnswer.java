package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Silences the generator's warning about a posted handler that answers with an object.
 *
 * <p>Every other answer travels as a value from the closed vocabulary; an object answer is a global reference built
 * in the caller's VM and handed over - the most expensive crossing the bridge has. Building it happens on the peer's
 * main thread, and it can wait on a collection of the caller's heap, so it is legal only for a rare call whose caller
 * is <b>already blocked</b> on the answer: the cost is paid once, where nothing else is waiting.</p>
 *
 * <p>That is exactly what the warning says, so annotating a handler is a decision to accept it: the processor then
 * emits the row instead of warning about it. Use it only when the call site is a synchronous post - see
 * {@link ArtPostHandler}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface AcceptObjectAnswer {
}
