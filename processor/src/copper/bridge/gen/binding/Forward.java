package copper.bridge.gen.binding;

import copper.bridge.gen.TypeRef;
import java.util.List;

/**
 * One native method and the entry that performs it: the declaration supplies the name and descriptor the VM binds
 * by, the annotation the entry, and neither is written down twice.
 */
final class Forward {
    final String owner;
    final String path;
    final String simpleName;
    final String method;
    final String descriptor;
    final String entry;
    final String entryNamespace;
    final String entryName;
    final String receiver;
    final TypeRef result;
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

    String table() {
        return copper.bridge.gen.Names.constant(simpleName) + "_NATIVES";
    }

    String parameters() {
        StringBuilder text = new StringBuilder();
        for (TypeRef param : params)
            text.append(", ").append(param.jni);
        return text.toString();
    }

    String signature() {
        StringBuilder text = new StringBuilder("(").append(receiver);
        for (TypeRef param : params)
            text.append(", ").append(param.jni);
        return text.append(") ").append(result.jni).toString();
    }
}
