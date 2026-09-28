package copper.bridge.gen;

/** One value that may cross the bridge, described the way every generator and table needs it. */
public final class TypeRef {
    public final String canonical;
    public final String code;
    public final String descriptor;
    public final String java;
    public final String jni;
    public final String jvalue;
    public final boolean object;
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
