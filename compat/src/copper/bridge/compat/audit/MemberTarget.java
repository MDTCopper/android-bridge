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

    MemberTarget(String owner, Kind kind, String name, int arity) {
        this.owner = owner;
        this.outer = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
        this.kind = kind;
        this.name = name;
        this.arity = arity;
    }

    static MemberTarget method(String owner, String name, String descriptor) {
        Type method = Type.getMethodType(descriptor);
        return new MemberTarget(owner, "<init>".equals(name) ? Kind.CONSTRUCTOR : Kind.METHOD, name,
                method.getArgumentTypes().length);
    }

    static MemberTarget field(String owner, String name) {
        return new MemberTarget(owner, Kind.FIELD, name, -1);
    }

    static MemberTarget type(String owner) {
        return new MemberTarget(owner, Kind.CLASS, owner, -1);
    }

    String key() {
        switch (kind) {
            case CLASS:
                return "C:" + owner;
            case METHOD:
            case CONSTRUCTOR:
                return "M:" + owner + "#" + name + "/" + arity;
            default:
                return "F:" + owner + "." + name;
        }
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
