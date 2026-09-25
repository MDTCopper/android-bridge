package copper.bridge.gen;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.lang.model.type.TypeMirror;

/**
 * The closed set of types the bridge lets across. A type outside it is rejected where it is
 * written rather than at the crossing, because the far side rebuilds every value from a
 * description and has no recipe for it.
 */
public final class Vocabulary {
    private static final Map<String, TypeRef> TYPES = new LinkedHashMap<>();

    static {
        add("boolean", "Z", "Z", "boolean", "jboolean", "z", false);
        add("byte", "B", "B", "byte", "jbyte", "b", false);
        add("char", "C", "C", "char", "jchar", "c", false);
        add("short", "S", "S", "short", "jshort", "s", false);
        add("int", "I", "I", "int", "jint", "i", false);
        add("long", "J", "J", "long", "jlong", "j", false);
        add("float", "F", "F", "float", "jfloat", "f", false);
        add("double", "D", "D", "double", "jdouble", "d", false);
        add("void", "V", "V", "void", "void", "i", false, true);

        add("java.lang.String", "L", "Ljava/lang/String;", "String", "jstring", "l", true);

        add("boolean[]", "z", "[Z", "boolean[]", "jbooleanArray", "l", true);
        add("byte[]", "b", "[B", "byte[]", "jbyteArray", "l", true);
        add("char[]", "c", "[C", "char[]", "jcharArray", "l", true);
        add("short[]", "s", "[S", "short[]", "jshortArray", "l", true);
        add("int[]", "i", "[I", "int[]", "jintArray", "l", true);
        add("long[]", "j", "[J", "long[]", "jlongArray", "l", true);
        add("float[]", "f", "[F", "float[]", "jfloatArray", "l", true);
        add("double[]", "d", "[D", "double[]", "jdoubleArray", "l", true);
        add("java.lang.String[]", "l", "[Ljava/lang/String;", "String[]", "jobjectArray", "l", true);
    }

    private Vocabulary() {
    }

    private static void add(String canonical, String code, String descriptor, String java, String jni,
            String jvalue, boolean object) {
        add(canonical, code, descriptor, java, jni, jvalue, object, false);
    }

    private static void add(String canonical, String code, String descriptor, String java, String jni,
            String jvalue, boolean object, boolean isVoid) {
        TYPES.put(canonical, new TypeRef(canonical, code, descriptor, java, jni, jvalue, object, isVoid));
    }

    /** The description of a type written in source, or {@code null} when it is outside the table. */
    public static TypeRef parse(String source) {
        String name = source.trim();
        // An outcome is written on an annotation, where the simple name is all there is room for.
        if (name.equals("String"))
            name = "java.lang.String";
        else if (name.equals("String[]"))
            name = "java.lang.String[]";
        return TYPES.get(name);
    }

    /** The description of a declared type, or {@code null} when it is outside the table. */
    public static TypeRef of(TypeMirror mirror) {
        return TYPES.get(mirror.toString());
    }

    /** A comma-separated list of the names this table accepts, for a diagnostic. */
    public static String names() {
        return String.join(", ", TYPES.keySet());
    }
}
