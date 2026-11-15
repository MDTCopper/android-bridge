package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks the code that deliberately handles a compatibility target whose presence changes inside the branch window.
 * Audits suppress findings for the listed owners; the reason records why the target is handled here.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface BridgeHandled {
    /** Owner prefixes whose transient findings are handled at this site. */
    String[] value();

    String reason();
}
