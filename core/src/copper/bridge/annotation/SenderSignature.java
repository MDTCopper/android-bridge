package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The source shapes a record's or a header's sequence parameter may be written as. A header field takes
 * exactly one form: the sending side has no overloads.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface SenderSignature {
    Class<?>[] value();
}
