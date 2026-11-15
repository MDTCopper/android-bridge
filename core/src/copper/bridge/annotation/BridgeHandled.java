package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Declares exact compatibility findings as handled. Each target is {@code type} or {@code type:member}; one
 * annotation carries every target that shares its warning and reason.
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
        REFLECTED_MEMBER_MISSING_FROM_ENDPOINTS
    }

    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
    @interface List {
        BridgeHandled[] value();
    }
}
