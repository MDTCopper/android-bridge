package copper.bridge.compat.refs;

/** One member reference the branch's class files encode, and the file that made it. */
public final class ClassRef {
    /** Internal name of the bridge class that references the member. */
    public final String sourceClass;
    public final String owner;
    public final String name;
    public final String descriptor;
    /** The {@code @CompatRange} floor and ceiling of the source class, or null when it has none. */
    public final String overrideKey;

    public ClassRef(String sourceClass, String owner, String name, String descriptor, String overrideKey) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.overrideKey = overrideKey;
    }

    public boolean isArc() {
        return owner.startsWith("arc/");
    }

    public String key() {
        return sourceClass + "|" + owner + "." + name + ":" + descriptor;
    }
}
