package copper.bridge.compat.refs;

/** One method annotated with {@code @BridgeInterface}, with the class that declares it. */
public final class BridgeInterfaceRef {
    public final String sourceClass;
    public final String owner;
    public final String name;
    public final String descriptor;

    public BridgeInterfaceRef(String sourceClass, String owner, String name, String descriptor) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public String key() {
        return owner + "." + name + descriptor;
    }
}
