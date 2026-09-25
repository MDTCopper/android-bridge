package copper.bridge.gen.binding;

import copper.bridge.gen.TypeRef;
import java.util.ArrayList;
import java.util.List;

/**
 * One member native reaches for by name, and how the generated call reaches it.
 *
 * <p>Which VM's environment the call needs, and whether the calling thread is coming back, come from the
 * annotation. The answer goes through a pointer because the return value is how the call went.</p>
 */
final class Reverse {
    /** The class the member belongs to, as Java names it: the comment and the diagnostics use it. */
    final String owner;
    /** The same class as {@code FindClass} wants it. */
    final String path;
    /** The name native looks the member up by. */
    final String method;
    /** The JNI descriptor of the declaration, which is the lookup's descriptor too. */
    final String descriptor;
    /** The generated entry's name: the member's own, capitalized. */
    final String entry;
    /** {@code BOTH}, {@code ART} or {@code JVM}: the environment the call has to be made in. */
    final String side;
    /** Whether the calling thread gives its attachment back before the call returns. */
    final boolean detaches;
    /** What the call takes, in JNI terms. */
    final List<TypeRef> params;
    /** What the call answers with, in JNI terms. */
    final TypeRef result;

    Reverse(String owner, String path, String method, String descriptor, String entry, String side,
            boolean detaches, List<TypeRef> params, TypeRef result) {
        this.owner = owner;
        this.path = path;
        this.method = method;
        this.descriptor = descriptor;
        this.entry = entry;
        this.side = side;
        this.detaches = detaches;
        this.params = params;
        this.result = result;
    }

    /** Whether the caller is already on the VM that owns the member, and hands its own environment over. */
    boolean bothSides() {
        return "BOTH".equals(side);
    }

    /** The C++ spelling of the side the call is made in. */
    String cppSide() {
        return "ART".equals(side) ? "Art" : "Jvm";
    }

    /** The caller's environment when it has one, then the answer's pointer. */
    List<String> parameters() {
        List<String> parameters = new ArrayList<>();
        if (bothSides())
            parameters.add("JNIEnv* env");
        for (int i = 0; i < params.size(); i++)
            parameters.add(params.get(i).jni + " arg" + i);
        if (!result.isVoid)
            parameters.add(result.jni + "* out");
        return parameters;
    }

    /** The declaration's own parameter list, as the generated comment writes it. */
    String declaredParams() {
        List<String> names = new ArrayList<>();
        for (TypeRef param : params)
            names.add(param.java);
        return String.join(", ", names);
    }

    /** The JNI call that performs this member. */
    String callMethod() {
        if (result.isVoid)
            return "CallStaticVoidMethod";
        if (result.object)
            return "CallStaticObjectMethod";
        switch (result.code) {
            case "Z": return "CallStaticBooleanMethod";
            case "B": return "CallStaticByteMethod";
            case "C": return "CallStaticCharMethod";
            case "S": return "CallStaticShortMethod";
            case "I": return "CallStaticIntMethod";
            case "J": return "CallStaticLongMethod";
            case "F": return "CallStaticFloatMethod";
            case "D": return "CallStaticDoubleMethod";
            default: return "CallStaticObjectMethod";
        }
    }
}
