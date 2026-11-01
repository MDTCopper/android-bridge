package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * A JVM-side handler of a posted call: ART queues it and the game loop performs it.
 *
 * <p>Reserved: nothing declares it today. Its cost is a frame of the game's budget, and a non-void return blocks
 * ART's main thread for that frame.</p>
 *
 * <pre>{@code
 * @JvmPostHandler                                     // ArtCall.<name>()
 * public void windowFocusChanged(boolean focused) {
 * }
 * }</pre>
 *
 * <p>Same rules as {@link ArtPostHandler}: instance method of a class, not private, not overriding a supertype's;
 * {@link #callbacks() outcomes} take a leading {@code long request}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvmPostHandler {
    /** Outcomes of an asynchronous call; see {@link ArtPostHandler#callbacks()}. */
    String[] callbacks() default {};
}
