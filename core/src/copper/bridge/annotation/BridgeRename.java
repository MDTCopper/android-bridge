package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * The name the packer writes for this method. It exists because Java cannot declare two methods that differ only in
 * return type, while the JVM method descriptor includes the return type and can hold both.
 *
 * <p>A branch compiles against one epoch. When a game method changes only its return type inside the branch range,
 * the source must give the second shape a distinct Java name and point at the real name here. The enhancer then:
 *
 * <ol>
 *   <li>renames the annotated declaration to {@link #value()};</li>
 *   <li>rewrites calls that use the source name to the real name;</li>
 *   <li>checks the complete method table for duplicate name + descriptor pairs;</li>
 *   <li>removes the annotation before the classes are packed.</li>
 * </ol>
 *
 * <p>Example - arc's {@code Sound.play} returns {@code long} in 84/85 and {@code int} from 86 on. One Java source
 * file cannot declare both under the same parameter list, so the older shape gets a source-only name and the runtime
 * name here:
 *
 * <pre>{@code
 * public class LegacySound implements Sound {
 *
 *     @BridgeOverride
 *     public int play(float volume, float pitch, float pan) {
 *         return JvmCall.playSound(...);
 *     }
 *
 *     @BridgeRename("play")
 *     @BridgeOverride
 *     public long playLong(float volume, float pitch, float pan) {
 *         return JvmCall.playSound(...);
 *     }
 * }
 * }</pre>
 *
 * <p>After enhancement the class carries:
 *
 * <pre>{@code
 * public int  play(float, float, float);
 * public long play(float, float, float);
 * }</pre>
 *
 * <p>The two descriptors differ in their return type, so both link on the versions that need them. A caller inside
 * the branch may call either {@code play} or {@code playLong}; both calls become {@code play} with the matching
 * descriptor.
 *
 * <p>The annotation does not create a new method. The declared parameter types, receiver and return type are kept.
 * The value must be a valid Java identifier, must not be a constructor name, and the resulting table must not contain
 * two methods with the same name and descriptor; the enhancer fails the branch otherwise. Use it together with
 * {@link BridgeOverride} when the method also overrides a game method, so the override check still sees it.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeRename {
    /** The method name the branch must carry in the class file. */
    String value();
}
