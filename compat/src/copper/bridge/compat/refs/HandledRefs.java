package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Collects the exact type, member and check kind declared handled by {@code @BridgeHandled}. */
public final class HandledRefs {
    private static final String BRIDGE_HANDLED = "Lcopper/bridge/annotation/BridgeHandled;";
    private static final String BRIDGE_HANDLED_LIST = "Lcopper/bridge/annotation/BridgeHandled$List;";

    public enum Kind {
        TYPE, MEMBER, SIGNATURE, REFLECTION
    }

    private static final class Entry {
        final String type;
        final String member;
        final Kind kind;

        Entry(String type, String member, Kind kind) {
            this.type = type;
            this.member = member;
            this.kind = kind;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

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

    public boolean handles(String type, String member, Kind kind) {
        for (Entry entry : entries) {
            if (!entry.type.equals(type) || entry.kind != kind)
                continue;
            if (kind == Kind.TYPE)
                return true;
            if (entry.member.equals(member))
                return true;
        }
        return false;
    }

    private void annotations(List<AnnotationNode> annotations) {
        if (annotations == null)
            return;
        for (AnnotationNode annotation : annotations) {
            if (BRIDGE_HANDLED.equals(annotation.desc)) {
                add(annotation);
            } else if (BRIDGE_HANDLED_LIST.equals(annotation.desc) && annotation.values != null) {
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (!"value".equals(annotation.values.get(i)))
                        continue;
                    Object value = annotation.values.get(i + 1);
                    if (value instanceof List) {
                        for (Object element : (List<?>)value) {
                            if (element instanceof AnnotationNode)
                                add((AnnotationNode)element);
                        }
                    }
                }
            }
        }
    }

    private void add(AnnotationNode annotation) {
        String type = null, member = "", kind = null;
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String name = (String)annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if ("type".equals(name))
                    type = (String)value;
                else if ("member".equals(name))
                    member = (String)value;
                else if ("kind".equals(name) && value instanceof String[])
                    kind = ((String[])value)[1];
            }
        }
        if (type != null && kind != null)
            entries.add(new Entry(type, member, Kind.valueOf(kind)));
    }
}
