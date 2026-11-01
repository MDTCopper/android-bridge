package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * An ART-side handler of an event the JVM posts: the ART main thread performs it, and nothing comes back.
 *
 * <pre>{@code
 * @ArtEventHandler                                    // JvmCall.onFirstFrameDrawn()
 * public void onFirstFrameDrawn() {
 * }
 * }</pre>
 *
 * <p>Named like an Android callback, {@code onXxx}; a host subclasses the declaring class to serve it, which is why
 * it may be declared on an interface with a no-op {@code default}. No return value, and the name is the bus name, so
 * it appears once in the bridge.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtEventHandler {
}
