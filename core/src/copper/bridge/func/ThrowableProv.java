package copper.bridge.func;

/** A supplier that may throw, unlike {@link java.util.function.Supplier}: it allows checked exceptions. */
public interface ThrowableProv<T> {
    T get() throws Throwable;
}
