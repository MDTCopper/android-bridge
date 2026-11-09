package copper.bridge.util;

import java.lang.invoke.*;

/**
 * Turns a method that may be absent on some versions into a plain functional interface at startup.
 *
 * <p>The lookup and the lambda are paid once; the frame loop calls the generated lambda, whose body is a direct
 * call. This keeps reflection out of the hot path while still selecting a newer method only where it exists.
 */
public final class MethodLambdas {
    private MethodLambdas() {
    }

    /** A {@link Runnable} for a no-argument static method, or null when the method is absent. */
    public static Runnable staticRunnable(Class<?> owner, String name) {
        try {
            MethodHandle handle = MethodHandles.lookup()
                    .findStatic(owner, name, MethodType.methodType(void.class));
            return runnable(handle);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A {@link Runnable} for a no-argument instance method bound to this receiver, or null when absent. */
    public static Runnable boundRunnable(Object receiver, String name) {
        if (receiver == null)
            return null;
        try {
            MethodHandle handle = MethodHandles.lookup()
                    .findVirtual(receiver.getClass(), name, MethodType.methodType(void.class))
                    .bindTo(receiver);
            return runnable(handle);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Runnable runnable(MethodHandle handle) throws Throwable {
        CallSite site = LambdaMetafactory.metafactory(
                MethodHandles.lookup(),
                "run",
                MethodType.methodType(Runnable.class),
                MethodType.methodType(void.class),
                handle,
                MethodType.methodType(void.class));
        return (Runnable)site.getTarget().invoke();
    }
}
