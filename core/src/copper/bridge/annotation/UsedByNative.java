package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks a member native reaches by name instead of by linkage: there is no {@code native} method on the Java side, so
 * the bridge would otherwise have no idea the member exists.
 *
 * <p>This is the reverse direction of {@link Native}. A native entry that has to call back into Java (a stream read
 * from the VM's own hooks, the "the JVM is gone, leave this screen" request, a log line handed to the Java logger)
 * looks the member up in the generated table by name, so the declaration has to say it is part of the bridge even
 * with no Java caller - which is why {@code @SuppressWarnings("unused")} sits beside it at every call site.</p>
 *
 * <p>Only a <b>static</b> method can be reached: the generated lookup carries no instance, and a member that answers
 * with a value cannot combine that with {@link #detaches()} - the answer would be released together with the thread's
 * references. Filters, fields and constructors are rejected by the processor. The generated entry name is the member's
 * name with its first letter capitalized, so no two annotated members may share that capitalized name.</p>
 *
 * <p>Examples: {@code BridgeActivity#endAfterJvmExit} (side {@code ART}, {@code detaches} - the thread parks),
 * {@code NativeLogBackend} line entry, {@code Bridge.load}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface UsedByNative {

    /** Which VM's environment the call must be made in; {@code BOTH} means the caller already has it. */
    enum Side {
        BOTH,
        ART,
        JVM
    }

    /** Which VM's {@code JNIEnv} the entry needs; {@code BOTH} when either is fine, {@code ART}/{@code JVM} when the
     *  generated lookup has to attach to that one. */
    Side side() default Side.BOTH;

    /**
     * Whether the calling thread detaches before the call returns; set only for a thread that parks for good, whose
     * attachment the VM would otherwise never release.
     *
     * <p>Only legal on a member that answers with nothing: an answer built for a detaching thread would be released
     * with the thread's own references, so the processor refuses the combination.</p>
     */
    boolean detaches() default false;
}
