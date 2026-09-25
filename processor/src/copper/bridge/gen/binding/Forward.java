package copper.bridge.gen.binding;

import copper.bridge.gen.TypeRef;
import java.util.List;

/**
 * One native method and the entry that performs it: the declaration supplies the name and descriptor the
 * VM binds by, the annotation the entry, and neither is written down twice.
 */
final class Forward {
    /** The class that declares it, as Java names it. */
    final String owner;
    /** The same class as {@code FindClass} wants it. */
    final String path;
    /** The class's simple name, which its table symbol is derived from. */
    final String simpleName;
    /** Also the table entry's name. */
    final String method;
    /** Also the table entry's descriptor. */
    final String descriptor;
    /** The entry as the annotation wrote it, below {@code copper::bridge}. */
    final String entry;
    /** Fully qualified, e.g. {@code copper::bridge::jre::Loader}. */
    final String entryNamespace;
    final String entryName;
    /** What the JNI passes as the second parameter: {@code jclass} or {@code jobject}. */
    final String receiver;
    /** What the entry returns, in JNI terms. */
    final TypeRef result;
    /** What the entry takes after the receiver, in JNI terms. */
    final List<TypeRef> params;

    Forward(String owner, String path, String simpleName, String method, String descriptor, String entry,
            String entryNamespace, String entryName, String receiver, TypeRef result, List<TypeRef> params) {
        this.owner = owner;
        this.path = path;
        this.simpleName = simpleName;
        this.method = method;
        this.descriptor = descriptor;
        this.entry = entry;
        this.entryNamespace = entryNamespace;
        this.entryName = entryName;
        this.receiver = receiver;
        this.result = result;
        this.params = params;
    }

    /** The symbol the table this row belongs to is defined under. */
    String table() {
        return copper.bridge.gen.Names.constant(simpleName) + "_NATIVES";
    }

    /** The parameters a forward declaration adds after the receiver. */
    String parameters() {
        StringBuilder text = new StringBuilder();
        for (TypeRef param : params)
            text.append(", ").append(param.jni);
        return text.toString();
    }

    /** What makes this declaration distinct from another of the same name. */
    String signature() {
        StringBuilder text = new StringBuilder("(").append(receiver);
        for (TypeRef param : params)
            text.append(", ").append(param.jni);
        return text.append(") ").append(result.jni).toString();
    }
}
