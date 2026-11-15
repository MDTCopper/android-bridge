package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Collects the owner prefixes declared handled by {@code @BridgeHandled}. */
public final class HandledRefs {
    private static final String BRIDGE_HANDLED = "Lcopper/bridge/annotation/BridgeHandled;";
    private final List<String> patterns = new ArrayList<>();

    private HandledRefs() {
    }

    public static HandledRefs read(Path directory) {
        HandledRefs handled = new HandledRefs();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                try (InputStream in = Files.newInputStream(path)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
                    handled.annotations(node.visibleAnnotations);
                    handled.annotations(node.invisibleAnnotations);
                    for (FieldNode field : node.fields) {
                        handled.annotations(field.visibleAnnotations);
                        handled.annotations(field.invisibleAnnotations);
                    }
                    for (MethodNode method : node.methods) {
                        handled.annotations(method.visibleAnnotations);
                        handled.annotations(method.invisibleAnnotations);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return handled;
    }

    public boolean handles(String owner) {
        for (String pattern : patterns) {
            if (owner.equals(pattern) || owner.startsWith(pattern + ".") || owner.startsWith(pattern + "#")
                    || owner.startsWith(pattern + "$"))
                return true;
        }
        return false;
    }

    private void annotations(List<AnnotationNode> annotations) {
        if (annotations == null)
            return;
        for (AnnotationNode annotation : annotations) {
            if (!BRIDGE_HANDLED.equals(annotation.desc) || annotation.values == null)
                continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (!"value".equals(annotation.values.get(i)))
                    continue;
                Object value = annotation.values.get(i + 1);
                if (value instanceof List) {
                    for (Object element : (List<?>)value) {
                        if (element instanceof String)
                            patterns.add((String)element);
                    }
                } else if (value instanceof String) {
                    patterns.add((String)value);
                }
            }
        }
    }
}
