package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a branch method as an override of a game supertype. This is deliberately separate from {@code @Override}:
 * the checker uses the distinction to decide whether a declaration must mirror the game or the bridge.
 *
 * <p>The branch compiles against the newest epoch in its range. A method that exists in that epoch may be missing, or
 * have another shape, at the older end. The checker therefore reads every declared method and requires this annotation
 * when a game supertype declares the same name and descriptor at either end. It also rejects the annotation when no
 * game supertype declares that method at either end. Methods declared by a bridge interface keep {@code @Override}.
 *
 * <p>Example - the game's {@code Graphics} declares these methods, so the branch marks both:
 *
 * <pre>{@code
 * public class BridgeGraphics extends Graphics {
 *
 *     @BridgeOverride
 *     public void configure() {
 *         // JVM-side implementation
 *     }
 *
 *     @BridgeOverride
 *     public void setContinuousRendering(boolean continuous) {
 *     }
 * }
 * }</pre>
 *
 * <p>Example - this interface belongs to the bridge, not the game, so it uses plain {@code @Override}:
 *
 * <pre>{@code
 * public final class BridgeInput implements Input {
 *
 *     @Override
 *     public void addListener(InputProcessor processor) {
 *     }
 * }
 * }</pre>
 *
 * <p>Putting {@code @BridgeOverride} on the bridge method above would fail the check, because no game supertype
 * declares it. Omitting it from a real game override also fails, because the annotation is the evidence that the
 * method was checked against both ends of the branch.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeOverride {
}
