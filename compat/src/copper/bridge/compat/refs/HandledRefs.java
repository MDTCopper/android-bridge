package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Collects the exact type, member and warning declared handled by {@code @BridgeHandled}. */
public final class HandledRefs {
    private static final String BRIDGE_HANDLED = "Lcopper/bridge/annotation/BridgeHandled;";
    private static final String BRIDGE_HANDLED_LIST = "Lcopper/bridge/annotation/BridgeHandled$List;";

    public enum Warning {
        TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        METHOD_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        FIELD_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        NESTED_TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW,
        METHOD_SIGNATURE_CHANGED_WITHIN_WINDOW,
        FIELD_TYPE_CHANGED_WITHIN_WINDOW,
        REFLECTED_TYPE_MISSING_FROM_ENDPOINTS,
        REFLECTED_MEMBER_MISSING_FROM_ENDPOINTS
    }

    private static final class Entry {
        final String type;
        final String member;
        final Warning warning;

        Entry(String type, String member, Warning warning) {
            this.type = type;
            this.member = member;
            this.warning = warning;
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

    public boolean handles(String type, String member, Warning warning) {
        for (Entry entry : entries) {
            if (!entry.type.equals(type) || entry.warning != warning)
                continue;
            if (warning == Warning.TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW
                    || warning == Warning.NESTED_TYPE_PRESENCE_NON_MONOTONIC_WITHIN_WINDOW)
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
        List<String> targets = new ArrayList<>();
        String warning = null;
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String name = (String)annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if ("targets".equals(name)) {
                    if (value instanceof List) {
                        for (Object element : (List<?>)value) {
                            if (element instanceof String)
                                targets.add((String)element);
                        }
                    } else if (value instanceof String) {
                        targets.add((String)value);
                    }
                } else if ("warning".equals(name) && value instanceof String[]) {
                    warning = ((String[])value)[1];
                }
            }
        }
        if (warning == null)
            return;
        Warning parsed = Warning.valueOf(warning);
        for (String target : targets) {
            int split = target.indexOf(':');
            String type = split < 0 ? target : target.substring(0, split);
            String member = split < 0 ? "" : target.substring(split + 1);
            entries.add(new Entry(type, member, parsed));
        }
    }
}
