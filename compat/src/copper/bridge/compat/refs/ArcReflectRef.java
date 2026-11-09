package copper.bridge.compat.refs;

/** A field named to arc's {@code Reflect} helper, with the branch class that makes the call. */
public final class ArcReflectRef {
    public final String sourceClass;
    public final String owner;
    public final String member;

    public ArcReflectRef(String sourceClass, String owner, String member) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.member = member;
    }

    public String key() {
        return owner + "." + member;
    }
}
