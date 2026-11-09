package copper.bridge.compat.check;

import copper.bridge.compat.api.ClassInfo;
import copper.bridge.compat.refs.BridgeInterfaceRef;
import copper.bridge.compat.sources.RefJars;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Checks that a {@code @BridgeInterface} method mirrors a method its hierarchy actually declares at one end of the
 * branch's range. A method no supertype ever declared is dead compatibility code, not an implementation.
 */
public final class BridgeInterfaceComparer {
    private final Map<String, ClassInfo> branchClasses = new HashMap<>();
    private final Map<String, ClassInfo> jarClasses = new HashMap<>();

    private BridgeInterfaceComparer() {
    }

    public static List<String> compare(List<BridgeInterfaceRef> refs, Range range, RefJars jars,
                                       Path bridgeClasses) {
        BridgeInterfaceComparer comparer = new BridgeInterfaceComparer();
        comparer.indexBranch(bridgeClasses);
        List<String> issues = new ArrayList<>();
        for (BridgeInterfaceRef ref : refs) {
            boolean floor = comparer.mirrors(ref, jars.jar(range.floor));
            boolean ceiling = comparer.mirrors(ref, jars.jar(range.ceiling));
            if (!floor && !ceiling)
                issues.add("bridge method is deadcode: " + ref.owner + "." + ref.name + ref.descriptor
                        + " has no supertype declaration at either end");
        }
        return issues;
    }

    private void indexBranch(Path directory) {
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String name = directory.relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                try {
                    branchClasses.put(name, ClassInfo.parse(Files.readAllBytes(path)));
                } catch (Throwable ignored) {
                    // the compiled branch was already read by the other checks
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean mirrors(BridgeInterfaceRef ref, copper.bridge.compat.api.JarIndex jar) {
        jarClasses.clear();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        ClassInfo owner = info(ref.owner, jar);
        if (owner == null)
            return false;
        enqueue(owner, queue, seen);

        boolean game = false;
        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            if (isGame(name))
                game = true;
            ClassInfo info = info(name, jar);
            if (info == null)
                continue;
            if (info.member(ref.name, ref.descriptor) != null)
                return true;
            enqueue(info, queue, seen);
        }
        // a class whose hierarchy never reaches the game is one of our own interfaces; not this check's subject
        return !game && branchClasses.containsKey(ref.owner);
    }

    private static void enqueue(ClassInfo info, Deque<String> queue, Set<String> seen) {
        if (info.superName != null && seen.add(info.superName))
            queue.addLast(info.superName);
        for (String itf : info.interfaces) {
            if (seen.add(itf))
                queue.addLast(itf);
        }
    }

    private ClassInfo info(String name, copper.bridge.compat.api.JarIndex jar) {
        ClassInfo branch = branchClasses.get(name);
        if (branch != null)
            return branch;
        ClassInfo cached = jarClasses.get(name);
        if (cached != null)
            return cached;
        ClassInfo fromJar = jar.classInfo(name);
        if (fromJar != null)
            jarClasses.put(name, fromJar);
        return fromJar;
    }

    private static boolean isGame(String name) {
        return name.startsWith("arc/") || name.startsWith("io/anuke/arc/")
                || name.startsWith("mindustry/") || name.startsWith("io/anuke/mindustry/");
    }
}
