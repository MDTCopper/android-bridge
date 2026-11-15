package copper.bridge.compat.audit;

import org.objectweb.asm.Type;

/** One declaration the branch depends on, reduced to what a Java source declaration can prove. */
final class MemberTarget {
    enum Kind {
        CLASS, METHOD, FIELD, CONSTRUCTOR
    }

    final String owner;
    final String outer;
    final Kind kind;
    final String name;
    /** Parameter count; -1 when reflection did not carry a descriptor. */
    final int arity;
    /** The source class's {@code @CompatRange} key, or null when it has none. */
    final String rangeKey;

    MemberTarget(String owner, Kind kind, String name, int arity) {
        this(owner, kind, name, arity, null);
    }

    MemberTarget(String owner, Kind kind, String name, int arity, String rangeKey) {
        this.owner = owner;
        this.outer = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
        this.kind = kind;
        this.name = name;
        this.arity = arity;
        this.rangeKey = rangeKey;
    }

    static MemberTarget method(String owner, String name, String descriptor, String rangeKey) {
        Type method = Type.getMethodType(descriptor);
        return new MemberTarget(owner, "<init>".equals(name) ? Kind.CONSTRUCTOR : Kind.METHOD, name,
                method.getArgumentTypes().length, rangeKey);
    }

    static MemberTarget field(String owner, String name, String rangeKey) {
        return new MemberTarget(owner, Kind.FIELD, name, -1, rangeKey);
    }

    static MemberTarget type(String owner, String rangeKey) {
        return new MemberTarget(owner, Kind.CLASS, owner, -1, rangeKey);
    }

    String key() {        switch (kind) {
            case CLASS:
                return "C:" + owner;
            case METHOD:
            case CONSTRUCTOR:
                return "M:" + owner + "#" + name + "/" + arity;
            default:
                return "F:" + owner + "." + name;
        }
    }

    /** Human-readable name for audit output. */
    String display() {
        String readable = owner.replace('/', '.');
        if (kind == Kind.CLASS)
            return readable;
        return readable + "." + ("<init>".equals(name) ? "constructor" : name);
    }

    /** Prefix of the source signature key recorded by the parser, or null when arity is unknown. */
    String signaturePrefix() {
        if (kind == Kind.METHOD || kind == Kind.CONSTRUCTOR)
            return arity < 0 ? null : "S:" + owner + "#" + name + "/" + arity + "=";
        return kind == Kind.FIELD ? "S:" + owner + "." + name + "=" : null;
    }

    boolean matches(String key) {
        if (kind == Kind.METHOD || kind == Kind.CONSTRUCTOR)
            return arity < 0 ? key.startsWith("M:" + owner + "#" + name + "/") : key.equals(key());
        return key.equals(key());
    }

    @Override
    public String toString() {
        String where = owner + (kind == Kind.CLASS ? "" : "." + name);
        return kind + " " + where + (arity < 0 ? "" : "/" + arity);
    }
}
