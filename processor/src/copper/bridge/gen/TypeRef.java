package copper.bridge.gen;

/** One value that may cross the bridge, described the way every generator and table needs it. */
public final class TypeRef {
    /** The type as the language names it, e.g. {@code java.lang.String[]} or {@code int}. */
    public final String canonical;
    /** The bridge's own type code: one character, lower case for the array form. */
    public final String code;
    /** The JNI descriptor, shared by the Java declaration and the C++ table. */
    public final String descriptor;
    /** The type as Java source writes it, e.g. {@code String[]}. */
    public final String java;
    /** The type as a JNI function parameter writes it, e.g. {@code jobjectArray}. */
    public final String jni;
    /** The jvalue member that carries it, {@code l} for everything by reference. */
    public final String jvalue;
    /** Whether the value is an object: it never fits in a jint slot. */
    public final boolean object;
    /** Whether this is {@code void}, which only a return type may be. */
    public final boolean isVoid;

    public TypeRef(String canonical, String code, String descriptor, String java, String jni, String jvalue,
            boolean object, boolean isVoid) {
        this.canonical = canonical;
        this.code = code;
        this.descriptor = descriptor;
        this.java = java;
        this.jni = jni;
        this.jvalue = jvalue;
        this.object = object;
        this.isVoid = isVoid;
    }

    public boolean isLong() {
        return "J".equals(code);
    }

    /** How many bytes a scalar type code takes on the wire, little-endian. */
    public static int size(String code) {
        switch (code) {
            case "Z":
            case "B": return 1;
            case "C":
            case "S": return 2;
            case "J":
            case "D": return 8;
            default: return 4;
        }
    }
}
