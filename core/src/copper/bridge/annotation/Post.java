package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Labels a generated call that is queued to the peer's main thread.
 *
 * <p>Written by the annotation processor, never by hand. Reading it on a generated method tells the caller which
 * channel a call takes: {@code @Post} is queued to the other VM's main thread (a non-void one blocks for the answer),
 * {@code @Direct} runs on the calling thread, and {@code @Event} is queued and never answered. The source is an
 * {@code @*PostHandler} declaration on the other side; see {@link ArtPostHandler}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Post {
}
