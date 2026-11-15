package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Declares one exact compatibility finding as handled. The type, member and check kind must all match; an
 * unhandled finding fails the audit.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Repeatable(BridgeHandled.List.class)
public @interface BridgeHandled {
    /** Internal name of the game or arc type, such as {@code arc/Net}. */
    String type();

    /** Member name, or empty only when {@link #kind} is {@link Kind#TYPE}. */
    String member() default "";

    Kind kind();

    String reason();

    enum Kind {
        TYPE, MEMBER, SIGNATURE, REFLECTION
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @interface List {
        BridgeHandled[] value();
    }
}
