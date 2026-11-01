package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * An ART-side handler of a posted call: the JVM queues it, the ART main thread performs it.
 *
 * <p>Anything that touches a view, starts an activity or does slow I/O belongs here.</p>
 *
 * <pre>{@code
 * @ArtPostHandler                                     // JvmCall.hide()
 * public void hide() {
 *     moveTaskToBack(true);
 * }
 *
 * @ArtPostHandler(callbacks = {"result(String[])", "canceled()"})   // answers later, by request id
 * public void showFileChooser(long request, ...) {
 * }
 * }</pre>
 *
 * <p>A {@code void} call returns at once; any other return makes the caller block until this handler answers. The
 * method name is the bus name, so it may appear once in the bridge. Instance method of a class, not private, not
 * overriding a supertype's; parameters and return type from the vocabulary: primitives, {@code String}, arrays of
 * either.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface ArtPostHandler {
    /**
     * Outcomes of an asynchronous call, each {@code name(type)}, answered through the generated
     * {@code ArtCall.<name>(request, ...)}: {@code "result(String[])"}, {@code "error(String)"}, {@code "canceled()"}.
     *
     * <p>Only with a leading {@code long request} and no return value; each outcome carries at most one
     * {@code String} or {@code String[]}.</p>
     */
    String[] callbacks() default {};
}
