package copper.bridge.annotation;

import java.lang.annotation.*;

/**
 * Marks a member native reaches by name instead of by linkage, so an annotated member is part of the bridge
 * even with no Java caller - which is why the {@code unused} suppression sits beside it.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface UsedByNative {

    /** Which VM's environment the call must be made in; BOTH means the caller already has it. */
    enum Side {
        BOTH,
        ART,
        JVM
    }

    Side side() default Side.BOTH;

    /**
     * Whether the calling thread detaches before the call returns; set only for a thread that parks for good,
     * whose attachment the VM would otherwise never release.
     */
    boolean detaches() default false;
}
