package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks a bridge reflection helper. The compatibility checker resolves the target at each call site from the named
 * arguments instead of guessing at the helper body, so a helper that takes a class and a member name can still be
 * checked against both ends of the version range.
 *
 * <p>The attribute positions refer to the call values, not to the helper parameters: for an instance helper the
 * receiver is value 0, then the arguments follow. {@link #owner()} or {@link #member()} can replace an argument when
 * the target is a constant. {@code void.class} means "read the class from {@link #classArg()}".
 *
 * <p>Example - the v1 loader helper receives both the class and the field name. The annotation says value 0 is the
 * class and value 1 is the field name:
 *
 * <pre>{@code
 * private static void configureArcLoader() {
 *     setIfPresent(SharedLibraryLoader.class, "isAndroid", true);
 *     setIfPresent(SharedLibraryLoader.class, "is64Bit", false);
 * }
 *
 * @BridgeReflect(kind = BridgeReflect.Kind.FIELD, classArg = 0, memberArg = 1)
 * private static void setIfPresent(Class<?> owner, String name, Object value) {
 *     Field field = owner.getDeclaredField(name);
 *     field.setAccessible(true);
 *     field.set(null, value);
 * }
 * }</pre>
 *
 * <p>The checker records {@code SharedLibraryLoader.isAndroid} and {@code SharedLibraryLoader.is64Bit} as field
 * targets. The helper body is not analysed, so it does not need a literal field name; an unresolved call argument
 * is reported as an issue instead.
 *
 * <p>Example - constant owner and constant member, with no class or name argument at the call site:
 *
 * <pre>{@code
 * @BridgeReflect(kind = BridgeReflect.Kind.CONSTRUCTOR, owner = Sound.class, member = "<init>")
 * private static Object newSound() throws Exception {
 *     return Sound.class.getDeclaredConstructor().newInstance();
 * }
 * }</pre>
 *
 * <p>Example - a method target where the name is the third value and the class is the first:
 *
 * <pre>{@code
 * @BridgeReflect(kind = BridgeReflect.Kind.METHOD, classArg = 0, memberArg = 2)
 * private static Object invoke(Class<?> owner, Object receiver, String method) {
 *     return owner.getMethod(method).invoke(receiver);
 * }
 * }</pre>
 *
 * <p>Use this annotation only on bridge-owned helpers. The game's own reflection helpers cannot carry it; their
 * shapes live in the checker's explicit catalog. A helper whose target depends on a value the checker cannot trace
 * (for example a name read from a field, a concatenated string, or a parameter of the helper) produces an issue
 * rather than silently disappearing from the audit.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface BridgeReflect {
    Kind kind();

    /** Index of the {@code Class} argument among the call values, receiver included. */
    int classArg() default 0;

    /** Constant target class; {@code void.class} means use {@link #classArg}. */
    Class<?> owner() default void.class;

    /** Index of the member-name argument, or -1 when {@link #member} supplies it. */
    int memberArg() default 1;

    /** Constant member name, used for constructors ({@code <init>}); empty when {@link #memberArg} supplies it. */
    String member() default "";

    enum Kind {
        TYPE, METHOD, FIELD, CONSTRUCTOR
    }
}
