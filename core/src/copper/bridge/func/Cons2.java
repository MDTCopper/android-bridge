package copper.bridge.func;

/** A two-argument consumer, mirroring {@link java.util.function.BiConsumer}. */
public interface Cons2<T, N> {
    void get(T var1, N var2);
}
