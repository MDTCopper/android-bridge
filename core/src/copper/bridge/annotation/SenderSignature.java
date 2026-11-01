package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * The source shapes a record's or a header's sequence parameter may be written as.
 *
 * <p>A Batch schema is an abstract class: header fields describe the fixed part, and one abstract method per kind of
 * record describes the payload. A sequence parameter has no type that says how it arrives - the sender may have it as
 * an array of the elements, as an {@code IntSeq}, or as a Java array of them - so the declaration lists the shapes it
 * accepts, and the generated writer gets one overload per listed shape.</p>
 *
 * <p>Rules the processor enforces: a record parameter with a sequence needs one (a caller cannot pass information the
 * writer has no form for), and every listed form has to be the wire shape of <em>that</em> parameter - a list of forms
 * that do not convert to it is rejected. A header field takes exactly one form: the sending side has no overloads
 * there, so the header is written once and every sender uses it.</p>
 *
 * <p>Example: {@code InputBatch}'s record methods list the array and sequence forms of their parameters.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface SenderSignature {
    /** The shapes the parameter may be written as, e.g. {@code {int[].class, IntSeq.class}}. */
    Class<?>[] value();
}
