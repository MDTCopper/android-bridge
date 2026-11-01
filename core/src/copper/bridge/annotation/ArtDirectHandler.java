package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * An ART-side handler of a direct call: it runs on the calling thread, no queue, no wake-up.
 *
 * <pre>{@code
 * @ArtDirectHandler                                   // JvmCall.getOsVersion()
 * public int getOsVersion() {
 *     return Build.VERSION.SDK_INT;
 * }
 * }</pre>
 *
 * <p>Only for a method that touches no view and starts no activity - a thread ART has never seen has no
 * {@code Looper}. Binder calls and static reads are fine; {@code startActivity} belongs on {@link ArtPostHandler},
 * even when the method returns a value. Instance method of a class, not static, not private.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtDirectHandler {
}
