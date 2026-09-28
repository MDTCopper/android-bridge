package copper.bridge.gen.binding;

import copper.bridge.gen.TypeRef;
import java.util.ArrayList;
import java.util.List;

/**
 * One member native reaches for by name, and how the generated call reaches it. The answer goes through a pointer
 * because the return value is how the call went.
 */
final class Reverse {
    final String owner;
    final String path;
    final String method;
    final String descriptor;
    final String entry;
    /** {@code BOTH}, {@code ART} or {@code JVM}: the environment the call has to be made in. */
    final String side;
    final boolean detaches;
    final List<TypeRef> params;
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

    String cppSide() {
        return "ART".equals(side) ? "Art" : "Jvm";
    }

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

    String declaredParams() {
        List<String> names = new ArrayList<>();
        for (TypeRef param : params)
            names.add(param.java);
        return String.join(", ", names);
    }

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
