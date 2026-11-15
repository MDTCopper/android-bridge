package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks the code that deliberately handles a compatibility target whose presence changes inside the branch window.
 * Only the listed check kinds are suppressed, so a new type, member, signature or reflection problem still shows.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface BridgeHandled {
    /** Owner prefixes whose findings are handled at this site. */
    String[] value();

    Kind[] kinds();

    /** Member names to narrow the suppression; empty means every member of the owner. */
    String[] members() default {};

    String reason();

    enum Kind {
        TYPE, MEMBER, SIGNATURE, REFLECTION
    }
}
