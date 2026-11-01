package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * A JVM-side handler of an event ART posts, performed on the game thread.
 *
 * <pre>{@code
 * @JvmEventHandler                                    // ArtCall.onSurfaceCreated(...)
 * default void onSurfaceCreated(int width, int height, long window) {
 * }
 * }</pre>
 *
 * <p>Declared in {@link copper.bridge.jvm.Events} and implemented by a branch - the processor cannot see a branch.
 * The no-op {@code default} lets a branch skip an event it cannot serve. Named like an Android callback,
 * {@code onXxx}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface JvmEventHandler {
}
