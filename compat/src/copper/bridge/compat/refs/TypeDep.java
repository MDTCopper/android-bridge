package copper.bridge.compat.refs;

/** One class the branch depends on, whether through a signature, a hierarchy edge, a class literal or an instruction. */
public final class TypeDep {
    public final String sourceClass;
    public final String owner;
    /** The source class's {@code @CompatRange} floor and ceiling, or null when it has none. */
    public final String overrideKey;

    public TypeDep(String sourceClass, String owner, String overrideKey) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.overrideKey = overrideKey;
    }

    public String key() {
        return sourceClass + "|" + owner + "|" + overrideKey;
    }

    @Override
    public String toString() {
        return owner;
    }
}
