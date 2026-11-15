package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.sources.RefJars;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Forces a branch class to implement every method its game supertype leaves abstract at the floor.
 *
 * <p>The branch compiles against the ceiling, so {@code javac} only enforces the methods that are abstract there.
 * A method that is abstract at the floor and default or gone at the ceiling is therefore easy to miss: the class
 * loads on the older device and the first call fails as an {@code AbstractMethodError}. This comparer reports any
 * such method the class does not declare. A method abstract at the ceiling as well is already enforced by the
 * compiler and is not reported here. A floor method whose descriptor names a type the ceiling no longer has cannot
 * be declared in source at all; it is printed as a warning instead of an error.
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
                // a method still abstract at the ceiling is enforced by compiling the branch against it
                if (Boolean.TRUE.equals(ceiling.get(entry.getKey())))
                    continue;
                if (comparer.implemented(type, entry.getKey(), jars.jar(range.floor)))
                    continue;
                // The branch compiles against the ceiling: a type that is gone there cannot appear in a source
                // declaration, so the floor method cannot be added without a compile-time placeholder.
                if (!representable(entry.getKey(), jars.jar(range.ceiling))) {
                    System.out.println("  warning: " + type.name + "." + entry.getKey()
                            + " is abstract at the floor but uses a type absent at the ceiling");
                    continue;
                }
                issues.add("method is abstract at floor but not implemented: " + type.name
                        + "." + entry.getKey() + " (floor " + range.floor + ", ceiling " + range.ceiling + ")");
            }
        }
        return issues;
    }

    /** Whether every type in the method descriptor exists in the ceiling jar, so source can name it. */
    private static boolean representable(String key, JarIndex ceiling) {
        Type method = Type.getMethodType(key.substring(key.indexOf('(')));
        for (Type type : method.getArgumentTypes()) {
            if (!present(type, ceiling))
                return false;
        }
        return present(method.getReturnType(), ceiling);
    }

    private static boolean present(Type type, JarIndex ceiling) {
        if (type.getSort() == Type.ARRAY)
            type = type.getElementType();
        return type.getSort() != Type.OBJECT || ceiling.classInfo(type.getInternalName()) != null;
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

    /** Whether this class, a branch superclass or a game superclass declares the method concrete at the floor. */
    private boolean implemented(ClassInfo type, String key, JarIndex floorJar) {
        String method = key.substring(0, key.indexOf('('));
        String descriptor = key.substring(method.length());
        String name = type.name;
        Set<String> seen = new HashSet<>();
        while (name != null && seen.add(name)) {
            ClassInfo info = branch.get(name);
            if (info == null)
                info = floorJar.classInfo(name);
            if (info == null)
                return false;
            MemberInfo member = info.member(method, descriptor);
            if (member != null && (member.access & Opcodes.ACC_ABSTRACT) == 0)
                return true;
            name = info.superName;
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
