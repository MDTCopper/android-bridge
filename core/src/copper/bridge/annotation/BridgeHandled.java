package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Declares exact compatibility findings as handled. The audit treats every unhandled finding as a build failure, so
 * this annotation is the only way to say "this change is expected and the code next to it handles it". It is exact
 * by design: the type, the member and the warning must all match.
 *
 * <p>Each target is either {@code internal/Type} for a type-level finding or {@code internal/Type:member} for a
 * member-level finding. One annotation may carry several targets when they share the same {@link #warning()} and
 * {@link #reason()}. Different warnings or different reasons need another annotation; the annotation is repeatable.
 *
 * <h2>Type presence oscillates</h2>
 *
 * <p>A type that is present, absent and present again (or absent, present and absent) inside the window is not a
 * one-way addition or removal. The audit reports it because the branch cannot link on the missing part. Example:
 * the 90 build replaces the arc net providers with {@code MClient}/{@code MServer}, and 91 restores them:
 *
 * <pre>{@code
 * @BridgeHandled(
 *     targets = {
 *         "io/anuke/mindustry/net/ArcNetClient",
 *         "io/anuke/mindustry/net/ArcNetServer",
 *         "io/anuke/mindustry/net/MClient",
 *         "io/anuke/mindustry/net/MServer"
 *     },
 *     warning = BridgeHandled.Warning.TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
 *     reason = "the 90 build replaced the net providers, 91 restored ArcNet"
 * )
 * @BridgeHandled(
 *     targets = {
 *         "io/anuke/mindustry/net/ArcNetClient:<init>",
 *         "io/anuke/mindustry/net/MClient:<init>"
 *     },
 *     warning = BridgeHandled.Warning.METHOD_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
 *     reason = "constructors exist only while their classes exist"
 * )
 * private static void installNetProviders() {
 *     // tries ArcNet, falls back to MClient/MServer
 * }
 * }</pre>
 *
 * <p>The reason is required: a suppression without a reason is not reviewable. A field uses
 * {@code FIELD_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW}; an inner class uses
 * {@code NESTED_TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW}.
 *
 * <h2>Source signature changes</h2>
 *
 * <p>A method whose parameter or return types differ between source revisions, and a field whose declared type
 * changes, are reported separately. Example: the two GL methods take {@code Buffer} on 65 and {@code IntBuffer} on
 * 83; the branch declares both overloads:
 *
 * <pre>{@code
 * @BridgeHandled(
 *     targets = {
 *         "io/anuke/arc/graphics/GL20:glGetActiveAttrib",
 *         "io/anuke/arc/graphics/GL20:glGetActiveUniform"
 *     },
 *     warning = BridgeHandled.Warning.METHOD_SIGNATURE_CHANGED_WITHIN_WINDOW,
 *     reason = "65 takes Buffer, 83 takes IntBuffer; both overloads are declared here"
 * )
 * public class BridgeGL20 implements GL20 {
 *     // both overloads
 * }
 * }</pre>
 *
 * <p>A field uses {@code FIELD_TYPE_CHANGED_WITHIN_WINDOW}.
 *
 * <h2>Reflection targets</h2>
 *
 * <p>Reflection findings have two directions. A target absent from both endpoint classpaths is an interior-only
 * probe; a target public at both endpoints does not need reflection for access. Example:
 *
 * <pre>{@code
 * @BridgeHandled(
 *     targets = {"io/anuke/mindustry/net/MClient:<init>", "io/anuke/mindustry/net/MServer:<init>"},
 *     warning = BridgeHandled.Warning.REFLECTED_MEMBER_MISSING_FROM_ENDPOINTS,
 *     reason = "looked up only when ArcNet is absent"
 * )
 * private static void installLegacyNetProviders() {
 *     // Class.forName("...MClient")
 * }
 * }</pre>
 *
 * <p>Use {@code REFLECTED_TYPE_MISSING_FROM_ENDPOINTS} for a whole reflected class, and the corresponding
 * {@code REFLECTED_*_PRESENT_AT_BOTH_ENDS} warnings when reflection is kept solely for a shape change rather than
 * for visibility or presence.
 *
 * <h2>Contract</h2>
 *
 * <ul>
 *   <li>Targets are exact. Prefixes and wildcards do not match; a typo leaves the finding unhandled and fails the
 *       build, which is intentional.</li>
 *   <li>A member warning requires {@code type:member}; {@code <init>} names a constructor. A type warning uses the
 *       bare type.</li>
 *   <li>The same target and warning may appear only when the code truly handles both shapes; otherwise the audit
 *       failure is the correct result.</li>
 *   <li>The audit prints the same warning name in brackets, such as
 *       {@code [METHOD_SIGNATURE_CHANGED_WITHIN_WINDOW]}, so the annotation and the finding can be read together.</li>
 * </ul>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Repeatable(BridgeHandled.List.class)
public @interface BridgeHandled {
    /** Targets as {@code internal/Type} or {@code internal/Type:member}. */
    String[] targets();

    Warning warning();

    String reason();

    enum Warning {
        /** A top-level class's presence oscillates: present, absent, present again (or the reverse). */
        TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        /** A method or constructor's presence oscillates inside the window. */
        METHOD_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        /** A field's presence oscillates inside the window. */
        FIELD_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        /** An inner class's presence oscillates inside the window. */
        NESTED_TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        /** A method's parameter or return types differ between source revisions. */
        METHOD_SIGNATURE_CHANGED_WITHIN_WINDOW,
        /** A field's declared type differs between source revisions. */
        FIELD_TYPE_CHANGED_WITHIN_WINDOW,
        /** A reflected class is absent from both endpoint classpaths. */
        REFLECTED_TYPE_MISSING_FROM_ENDPOINTS,
        /** A reflected method, field or constructor is absent from both endpoint classpaths. */
        REFLECTED_MEMBER_MISSING_FROM_ENDPOINTS,
        /** A reflected class is public at both endpoints, so reflection is not needed for access. */
        REFLECTED_TYPE_PRESENT_AT_BOTH_ENDS,
        /** A reflected member is public at both endpoints, so reflection is not needed for access. */
        REFLECTED_MEMBER_PRESENT_AT_BOTH_ENDS
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @interface List {
        BridgeHandled[] value();
    }
}
