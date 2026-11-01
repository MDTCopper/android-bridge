package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Labels a generated call that runs on the calling thread and returns a value: no queue, no hand-off, the peer's
 * handler runs in the caller's frame.
 *
 * <p>Written by the annotation processor, never by hand. The source is an {@code @*DirectHandler} declaration on the
 * other side, and a direct row always answers - that is why the generated method always returns something. See
 * {@link ArtDirectHandler} for what may be reached this way.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Direct {
}
