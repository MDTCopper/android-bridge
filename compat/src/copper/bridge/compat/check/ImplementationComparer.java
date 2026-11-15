package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.sources.RefJars;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Forces a branch class to implement a method whose game supertype declares it abstract at the floor and a default
 * method at the ceiling.
 *
 * <p>The branch compiles against the ceiling, so {@code javac} is satisfied with the default; the floor has no method
 * body, and the call only fails on that device, as an {@code AbstractMethodError}. The reverse direction and methods
 * abstract at both ends are already enforced by compilation, and methods that disappear at the ceiling are not an
 * abstract/default change; none of those are reported here.
 */
public final class ImplementationComparer {
    private final Map<String, ClassInfo> branch = new HashMap<>();
    private final Map<String, ClassInfo> classes = new HashMap<>();

    private ImplementationComparer() {
    }

    public static List<String> compareJar(Range branchRange, Map<String, Range> overrides, RefJars jars,
                                          Path bridgeClasses, Map<String, String> classRanges) {
        ImplementationComparer comparer = new ImplementationComparer();
        comparer.index(bridgeClasses);
        List<String> issues = new ArrayList<>();

        for (ClassInfo type : comparer.branch.values()) {
            if ((type.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT)) != 0)
                continue;

            String key = classRanges.get(type.name);
            Range range = key == null ? branchRange : overrides.get(key);
            if (range == null) {
                issues.add("no range for @CompatRange " + key + " on " + type.name);
                continue;
            }

            Map<String, Boolean> floor = comparer.abstractness(type, jars.jar(range.floor));
            Map<String, Boolean> ceiling = comparer.abstractness(type, jars.jar(range.ceiling));
            if (floor.isEmpty())
                continue;

            for (Map.Entry<String, Boolean> entry : floor.entrySet()) {
                if (!entry.getValue())
                    continue;
                // only an abstract-at-floor/default-at-ceiling change; the other directions cannot be left unimplemented
                if (!Boolean.FALSE.equals(ceiling.get(entry.getKey())))
                    continue;
                if (comparer.implemented(type, entry.getKey()))
                    continue;
                issues.add("method is abstract at floor but a default at ceiling and not implemented: " + type.name
                        + "." + entry.getKey() + " (floor " + range.floor + ", ceiling " + range.ceiling + ")");
            }
        }
        return issues;
    }

    private void index(Path directory) {
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String name = directory.relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                try {
                    branch.put(name, ClassInfo.parse(Files.readAllBytes(path)));
                } catch (Throwable ignored) {
                    // the class was already read by the other checks
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every method of the class's game ancestry, mapped to whether it is abstract at that end. */
    private Map<String, Boolean> abstractness(ClassInfo type, JarIndex jar) {
        classes.clear();
        Map<String, Boolean> result = new HashMap<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        enqueueSupertypes(type, queue, seen);
        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            ClassInfo info = info(name, jar);
            if (info == null)
                continue;
            for (MemberInfo member : info.members()) {
                if (member.name.startsWith("<") || !member.descriptor.startsWith("("))
                    continue;
                String key = member.name + member.descriptor;
                if (!result.containsKey(key))
                    result.put(key, isAbstract(type, key, jar));
            }
            enqueueSupertypes(info, queue, seen);
        }
        return result;
    }

    /**
     * Whether the method is abstract for this class at that end: a concrete method in the class chain wins, then a
     * default method satisfies it, and an abstract declaration anywhere in the interfaces makes it abstract.
     */
    private boolean isAbstract(ClassInfo type, String key, JarIndex jar) {
        String method = key.substring(0, key.indexOf('('));
        String descriptor = key.substring(method.length());

        // class chain: this class's superclass first, then its own interfaces
        String current = type.superName;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            ClassInfo info = info(current, jar);
            if (info == null)
                break;
            MemberInfo member = info.member(method, descriptor);
            if (member != null)
                return (member.access & Opcodes.ACC_ABSTRACT) != 0;
            current = info.superName;
        }

        boolean anyDefault = false;
        Deque<String> interfaces = new ArrayDeque<>();
        for (String itf : type.interfaces)
            interfaces.add(itf);
        current = type.superName;
        visited.clear();
        while (current != null && visited.add(current)) {
            ClassInfo info = info(current, jar);
            if (info == null)
                break;
            for (String itf : info.interfaces)
                interfaces.add(itf);
            current = info.superName;
        }
        visited.clear();
        while (!interfaces.isEmpty()) {
            String name2 = interfaces.removeFirst();
            if (!visited.add(name2))
                continue;
            ClassInfo info = info(name2, jar);
            if (info == null)
                continue;
            MemberInfo member = info.member(method, descriptor);
            if (member != null) {
                if ((member.access & Opcodes.ACC_ABSTRACT) != 0)
                    return true;
                anyDefault = true;
            }
            for (String itf : info.interfaces)
                interfaces.add(itf);
        }
        return false; // absent, or a default provides it
    }

    /** Whether this class or a branch superclass declares the method concrete. */
    private boolean implemented(ClassInfo type, String key) {
        String method = key.substring(0, key.indexOf('('));
        String descriptor = key.substring(method.length());
        ClassInfo current = type;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current.name)) {
            MemberInfo member = current.member(method, descriptor);
            if (member != null && (member.access & Opcodes.ACC_ABSTRACT) == 0)
                return true;
            current = branch.get(current.superName);
        }
        return false;
    }

    private void enqueueSupertypes(ClassInfo type, Deque<String> queue, Set<String> seen) {
        if (type.superName != null && seen.add(type.superName))
            queue.addLast(type.superName);
        for (String itf : type.interfaces) {
            if (seen.add(itf))
                queue.addLast(itf);
        }
    }

    private ClassInfo info(String name, JarIndex jar) {
        ClassInfo local = branch.get(name);
        if (local != null)
            return local;
        ClassInfo cached = classes.get(name);
        if (cached != null)
            return cached;
        ClassInfo fromJar = jar.classInfo(name);
        if (fromJar != null)
            classes.put(name, fromJar);
        return fromJar;
    }
}
