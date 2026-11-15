package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks a bridge reflection helper. The checker resolves the target at each call site from the named arguments, so
 * the helper body itself is not guessed at.
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
