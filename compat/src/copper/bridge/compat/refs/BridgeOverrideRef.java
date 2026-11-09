package copper.bridge.compat.refs;

/** One method annotated with {@code @BridgeOverride}, with the class that declares it. */
public final class BridgeOverrideRef {
    public final String sourceClass;
    public final String owner;
    public final String name;
    public final String descriptor;
    public boolean annotated;

    public BridgeOverrideRef(String sourceClass, String owner, String name, String descriptor) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String key() {
        return owner + "." + name + descriptor;
    }
}
