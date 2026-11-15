package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Collects the owner, member and check kinds declared handled by {@code @BridgeHandled}. */
public final class HandledRefs {
    private static final String BRIDGE_HANDLED = "Lcopper/bridge/annotation/BridgeHandled;";

    public enum Kind {
        TYPE, MEMBER, SIGNATURE, REFLECTION
    }

    private static final class Entry {
        final String owner;
        final Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        final Set<String> members = new HashSet<>();

        Entry(String owner) {
            this.owner = owner;
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

    public boolean handles(String owner, Kind kind) {
        return handles(owner, null, kind);
    }

    public boolean handles(String owner, String member, Kind kind) {
        for (Entry entry : entries) {
            if (!entry.kinds.contains(kind))
                continue;
            if (!matches(entry.owner, owner))
                continue;
            if (entry.members.isEmpty() || member == null || entry.members.contains(member))
                return true;
        }
        return false;
    }

    private static boolean matches(String pattern, String owner) {
        return owner != null && (owner.equals(pattern) || owner.startsWith(pattern + ".")
                || owner.startsWith(pattern + "#") || owner.startsWith(pattern + "$"));
    }

    private void annotations(List<AnnotationNode> annotations) {
        if (annotations == null)
            return;
        for (AnnotationNode annotation : annotations) {
            if (!BRIDGE_HANDLED.equals(annotation.desc) || annotation.values == null)
                continue;
            String owner = null;
            List<String> owners = new ArrayList<>();
            List<Kind> kinds = new ArrayList<>();
            List<String> members = new ArrayList<>();
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String name = (String)annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if ("value".equals(name))
                    addStrings(owners, value);
                else if ("kinds".equals(name))
                    addKinds(kinds, value);
                else if ("members".equals(name))
                    addStrings(members, value);
            }
            for (String value : owners) {
                Entry entry = new Entry(value);
                entry.kinds.addAll(kinds);
                entry.members.addAll(members);
                entries.add(entry);
            }
        }
    }

    private static void addStrings(List<String> target, Object value) {
        if (value instanceof List) {
            for (Object element : (List<?>)value) {
                if (element instanceof String)
                    target.add((String)element);
            }
        } else if (value instanceof String) {
            target.add((String)value);
        }
    }

    private static void addKinds(List<Kind> target, Object value) {
        if (value instanceof List) {
            for (Object element : (List<?>)value)
                addKind(target, element);
        } else {
            addKind(target, value);
        }
    }

    private static void addKind(List<Kind> target, Object value) {
        if (value instanceof String[])
            target.add(Kind.valueOf(((String[])value)[1]));
        else if (value instanceof String)
            target.add(Kind.valueOf((String)value));
    }
}
