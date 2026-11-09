package copper.bridge.compat.refs;

/** One class or member the branch reaches by reflection, and the file that names it. */
public final class ReflectionRef {
    public enum Kind {
        TYPE, METHOD, FIELD, CONSTRUCTOR
    }

    public final String sourceClass;
    public final String owner;
    public final String member;
    public final Kind kind;
    /** The {@code @CompatRange} floor and ceiling of the source class, or null when it has none. */
    public final String overrideKey;

    public ReflectionRef(String sourceClass, String owner, String member, Kind kind, String overrideKey) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.member = member;
        this.kind = kind;
        this.overrideKey = overrideKey;
    }

    public String key() {
        return sourceClass + "|" + owner + "." + member + ":" + kind;
    }

    @Override
    public String toString() {
        return owner + (member == null ? "" : "." + member) + " (" + kind + ")";
    }
}
