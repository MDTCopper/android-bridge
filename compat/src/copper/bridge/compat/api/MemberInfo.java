package copper.bridge.compat.api;

/** One field or method as a class file declares it. */
public final class MemberInfo {
    public final String name;
    public final String descriptor;
    public final int access;

    public MemberInfo(String name, String descriptor, int access) {
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
    }
}
