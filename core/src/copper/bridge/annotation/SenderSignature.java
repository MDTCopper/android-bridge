package copper.bridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The source shapes a record's sequence parameter, or a header's, may be written as.
 *
 * <p>A schema declares a sequence as a {@code Wire} buffer, the one shape both readable on the receiving side
 * and writable on the sending side; each listed form (an array, a {@code java.nio} buffer) becomes an overload
 * and must describe the same wire shape, so a different kind of sequence is a compile error rather than a silent
 * reinterpretation of the bytes. A header field takes exactly one form, because a field on the sending side has
 * no overloads.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface SenderSignature {
    Class<?>[] value();
}
