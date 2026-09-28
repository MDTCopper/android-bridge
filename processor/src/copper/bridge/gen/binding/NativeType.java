package copper.bridge.gen.binding;

import copper.bridge.gen.TypeRef;
import copper.bridge.gen.Vocabulary;
import javax.lang.model.type.TypeMirror;

/**
 * The types a native declaration may name: the wire vocabulary plus {@code Object}. {@code Object} never
 * crosses the bus - a value that crosses is rebuilt on the far side, and there is no recipe for an arbitrary
 * object - so this mapping must not widen what the bus accepts.
 */
final class NativeType {
    private static final TypeRef OBJECT =
            new TypeRef("java.lang.Object", "L", "Ljava/lang/Object;", "Object", "jobject", "l", true, false);

    private NativeType() {
    }

    /** The description of a declared type, or {@code null} when a binding cannot name it. */
    static TypeRef of(TypeMirror mirror) {
        if (mirror != null && "java.lang.Object".equals(mirror.toString()))
            return OBJECT;
        return Vocabulary.of(mirror);
    }
}
