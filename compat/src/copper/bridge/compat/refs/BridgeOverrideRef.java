package copper.bridge.compat.refs;

/** One method a branch class declares, with whether it carries {@code @BridgeOverride}. */
public final class BridgeOverrideRef {
    public final String sourceClass;
    public final String owner;
    public final String name;
    public final String descriptor;
    /** The {@code @CompatRange} of the source class, or null when it has none. */
    public final String overrideKey;
    public boolean annotated;

    public BridgeOverrideRef(String sourceClass, String owner, String name, String descriptor,
                             String overrideKey) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.overrideKey = overrideKey;
    }

    public String key() {
        return owner + "." + name + descriptor;
    }
}
