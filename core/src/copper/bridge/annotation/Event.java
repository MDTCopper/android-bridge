package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Labels a generated call that only tells the peer something: no handler runs in the caller's frame, nothing comes
 * back, and the caller has already moved on when the peer performs it.
 *
 * <p>Written by the annotation processor, never by hand - a generated {@code JvmCall}/{@code ArtCall} method carries
 * it so a reader can tell which channel the call takes without following the bus. The source of the call is a
 * {@code @*EventHandler} declaration on the other side; see {@link ArtEventHandler} and {@link JvmEventHandler}.</p>
 *
 * <p>Answers travel under this label too, since they share the event queue and the dispatch: a posted call that
 * declared {@link ArtPostHandler#callbacks() outcomes} answers with a generated {@code @Event} method as well.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Event {
}
