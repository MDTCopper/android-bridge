package copper.bridge.compat.check;

import copper.bridge.compat.api.ClassInfo;
import copper.bridge.compat.api.JarIndex;
import copper.bridge.compat.refs.BridgeOverrideRef;
import copper.bridge.compat.sources.RefJars;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Checks both directions of {@code @BridgeOverride}: a method the game hierarchy declares must carry the annotation,
 * and an annotated method must actually mirror a game method. A method that mirrors only our own interfaces keeps
 * {@code @Override}; that is how the two kinds stay distinguishable.
 */
public final class BridgeOverrideComparer {
    private final Map<String, ClassInfo> branchClasses = new HashMap<>();
    private final Map<String, ClassInfo> jarClasses = new HashMap<>();

    private BridgeOverrideComparer() {
    }

    public static List<String> compareJar(List<BridgeOverrideRef> refs, Range range, RefJars jars,
                                          Path bridgeClasses) {
        BridgeOverrideComparer comparer = new BridgeOverrideComparer();
        comparer.indexBranch(bridgeClasses);
        List<String> issues = new ArrayList<>();
        for (BridgeOverrideRef ref : refs) {
            boolean floor = comparer.gameMatch(ref, jars.jar(range.floor));
            boolean ceiling = comparer.gameMatch(ref, jars.jar(range.ceiling));
            if (ref.annotated && !floor && !ceiling) {
                issues.add("bridge method annotation is wrong: " + ref.owner + "." + ref.name + ref.descriptor
                        + " mirrors no game supertype at either end");
            } else if (!ref.annotated && (floor || ceiling)) {
                issues.add("game override is missing @BridgeOverride: " + ref.owner + "." + ref.name
                        + ref.descriptor);
            }
        }
        return issues;
    }

    public static List<String> compareCode(List<BridgeOverrideRef> refs, Range range,
                                           Path gameDir, Path arcDir, Path bridgeClasses) {
        SourceHierarchy floor = new SourceHierarchy(gameDir, range.floor, arcDir,
                range.arcFloor == null ? range.floor : range.arcFloor, bridgeClasses);
        SourceHierarchy ceiling = new SourceHierarchy(gameDir, range.ceiling, arcDir,
                range.arcCeiling == null ? range.ceiling : range.arcCeiling, bridgeClasses);
        List<String> issues = new ArrayList<>();
        for (BridgeOverrideRef ref : refs) {
            boolean floorMatch = floor.gameMatch(ref.owner, ref.name);
            boolean ceilingMatch = ceiling.gameMatch(ref.owner, ref.name);
            if (ref.annotated && !floorMatch && !ceilingMatch) {
                issues.add("bridge method annotation is wrong: " + ref.owner + "." + ref.name + ref.descriptor
                        + " mirrors no game supertype at either end");
            } else if (!ref.annotated && (floorMatch || ceilingMatch)) {
                issues.add("game override is missing @BridgeOverride: " + ref.owner + "." + ref.name
                        + ref.descriptor);
            }
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

    private boolean gameMatch(BridgeOverrideRef ref, JarIndex jar) {
        jarClasses.clear();
        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        ClassInfo owner = info(ref.owner, jar);
        if (owner == null)
            return false;
        enqueue(owner, queue, seen);

        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            ClassInfo info = info(name, jar);
            if (info == null)
                continue;
            if (info.member(ref.name, ref.descriptor) != null && isGame(name))
                return true;
            enqueue(info, queue, seen);
        }
        return false;
    }

    private static void enqueue(ClassInfo info, Deque<String> queue, Set<String> seen) {
        if (info.superName != null && seen.add(info.superName))
            queue.addLast(info.superName);
        for (String itf : info.interfaces) {
            if (seen.add(itf))
                queue.addLast(itf);
        }
    }

    private ClassInfo info(String name, JarIndex jar) {
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
