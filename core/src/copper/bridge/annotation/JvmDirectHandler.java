package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * A JVM-side handler of a direct call: ART calls it on the calling thread, no queue.
 *
 * <p>Reserved: nothing declares it today, and it is the hardest channel to use - the game state is single threaded,
 * so only a method indifferent to its thread may run this way, and ART's main thread must never wait on the game
 * loop.</p>
 *
 * <pre>{@code
 * @JvmDirectHandler                                   // ArtCall.<name>()
 * public boolean supportsInstancing() {
 *     return false;
 * }
 * }</pre>
 *
 * <p>Same declaration rules as {@link JvmPostHandler}: instance method of a class, not private, not overriding a
 * supertype's.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvmDirectHandler {
}
