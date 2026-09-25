package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks a member the native half reaches for by name.
 *
 * <p>Native never links against these members: it resolves each with {@code GetMethodID} or
 * {@code GetStaticMethodID} from the name and JNI descriptor in this declaration, so an annotated member is
 * part of the bridge even when no Java code calls it - which is what the {@code unused} suppression beside it
 * records. Fields are in the target list so a field native starts reading gets the same marker, though no field
 * is looked up today. {@code CLASS} retention, for tooling rather than the VM.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface UsedByNative {

    /** Which virtual machine's environment the call has to be made in. */
    enum Side {
        /** The member belongs to both, so the caller already has the environment it needs. */
        BOTH,
        /** The member belongs to ART, and the caller is not on ART. */
        ART,
        /** The member belongs to the JVM, and the caller is not on the JVM. */
        JVM
    }

    /** The environment the generated call reaches the member through. */
    Side side() default Side.BOTH;

    /**
     * Whether the calling thread gives its attachment back before the call returns.
     *
     * <p>An attachment normally lives until the thread ends, where the VM releases it - re-attaching
     * per call would pay that cost over and over. A thread that parks for good after the call never
     * reaches its own end, so the record it holds can never be released.</p>
     */
    boolean detaches() default false;
}
