package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Declares exact compatibility findings as handled. Each target is {@code type} or {@code type:member}; one
 * annotation carries every target that shares its kind and reason.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Repeatable(BridgeHandled.List.class)
public @interface BridgeHandled {
    /** Targets as {@code internal/Type} or {@code internal/Type:member}. */
    String[] targets();

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
