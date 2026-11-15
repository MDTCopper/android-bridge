package copper.bridge.compat.enhance;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Renames the methods a branch declared under a build name before the API check and packing.
 *
 * <p>javac cannot declare two methods that differ only in return type. A branch that has to implement a game shape
 * with another return type declares the alternate method under a different name and marks it {@code @BridgeRename};
 * this step checks the overload set that would result, then writes it under the game name. The check runs over the
 * whole branch before any class is written, so a bad rename fails the build instead of producing half a branch.
 *
 * <p>Usage: {@code --out <dir> --classes <branch>=<dir>...}.
 */
public final class Enhancer {
    private static final String NAME = "Lcopper/bridge/annotation/BridgeRename;";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    public static void main(String[] args) throws IOException {
        Map<String, List<String>> options = parse(args);
        Path out = Paths.get(single(options, "out"));
        Map<String, Path> branches = branches(options);

        if (Files.exists(out))
            delete(out);
        Files.createDirectories(out);

        for (Map.Entry<String, Path> branch : branches.entrySet()) {
            int renames = enhance(branch.getKey(), branch.getValue(), out.resolve(branch.getKey()));
            System.out.println(branch.getKey() + ": " + renames + " method name(s)");
        }
    }

    private static int enhance(String branch, Path source, Path target) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(source)) {
            files = stream.filter(path -> path.toString().endsWith(".class")).collect(Collectors.toList());
        }

        Map<String, ClassNode> classes = new LinkedHashMap<>();
        for (Path file : files) {
            String relative = source.relativize(file).toString().replace('\\', '/');
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(node, 0);
            classes.put(relative, node);
        }

        Map<String, String> renames = new HashMap<>();
        for (ClassNode node : classes.values())
            collect(node, renames);

        // Check every final method table before writing any class: a rename that would duplicate a descriptor or
        // replace a method already carrying the name is a declaration error, not a half-written branch.
        for (ClassNode node : classes.values())
            check(node, renames);

        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            Files.createDirectories(target.resolve(entry.getKey()).getParent());
            Files.write(target.resolve(entry.getKey()), write(entry.getValue(), renames));
        }
        return renames.size();
    }

    private static void collect(ClassNode node, Map<String, String> renames) {
        for (MethodNode method : node.methods) {
            AnnotationNode annotation = annotation(method);
            if (annotation == null)
                continue;
            if (method.name.startsWith("<"))
                throw new IllegalArgumentException("@BridgeRename on a constructor: " + node.name + "." + method.name);

            String name = value(annotation);
            if (name == null || !IDENTIFIER.matcher(name).matches())
                throw new IllegalArgumentException("@BridgeRename on " + node.name + "." + method.name
                        + " is not a method name: " + name);

            renames.put(key(node.name, method.name, method.desc), name);
            remove(method);
        }
    }

    private static void check(ClassNode node, Map<String, String> renames) {
        Map<String, MethodNode> methods = new HashMap<>();
        for (MethodNode method : node.methods) {
            String name = renames.getOrDefault(key(node.name, method.name, method.desc), method.name);
            String descriptor = name + method.desc;
            MethodNode previous = methods.putIfAbsent(descriptor, method);
            if (previous != null)
                throw new IllegalArgumentException("two methods become " + node.name + "." + name + method.desc
                        + ": " + previous.name + " and " + method.name);
        }
    }

    private static byte[] write(ClassNode node, Map<String, String> renames) {
        ClassWriter writer = new ClassWriter(0);
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                return renames.getOrDefault(key(owner, name, descriptor), name);
            }
        };
        node.accept(new ClassRemapper(writer, remapper));
        return writer.toByteArray();
    }

    private static AnnotationNode annotation(MethodNode method) {
        AnnotationNode found = find(method.visibleAnnotations);
        return found != null ? found : find(method.invisibleAnnotations);
    }

    private static AnnotationNode find(List<AnnotationNode> annotations) {
        if (annotations == null)
            return null;
        for (AnnotationNode annotation : annotations) {
            if (NAME.equals(annotation.desc))
                return annotation;
        }
        return null;
    }

    private static void remove(MethodNode method) {
        remove(method.visibleAnnotations);
        remove(method.invisibleAnnotations);
    }

    private static void remove(List<AnnotationNode> annotations) {
        if (annotations != null)
            annotations.removeIf(annotation -> NAME.equals(annotation.desc));
    }

    private static String value(AnnotationNode annotation) {
        if (annotation.values == null)
            return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if ("value".equals(annotation.values.get(i)))
                return (String)annotation.values.get(i + 1);
        }
        return null;
    }

    private static String key(String owner, String name, String descriptor) {
        return owner + "|" + name + "|" + descriptor;
    }

    private static Map<String, Path> branches(Map<String, List<String>> options) {
        Map<String, Path> branches = new LinkedHashMap<>();
        for (String value : options.getOrDefault("classes", Collections.emptyList())) {
            int split = value.indexOf('=');
            if (split <= 0)
                throw new IllegalArgumentException("--classes wants <branch>=<dir>: " + value);
            branches.put(value.substring(0, split), Paths.get(value.substring(split + 1)));
        }
        if (branches.isEmpty())
            throw new IllegalArgumentException("no --classes");
        return branches;
    }

    private static Map<String, List<String>> parse(String[] args) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (!args[i].startsWith("--"))
                continue;
            options.computeIfAbsent(args[i].substring(2), key -> new ArrayList<>()).add(args[i + 1]);
        }
        return options;
    }

    private static String single(Map<String, List<String>> options, String key) {
        List<String> values = options.get(key);
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("missing --" + key);
        return values.get(values.size() - 1);
    }

    private static void delete(Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList()))
                Files.deleteIfExists(entry);
        }
    }
}
