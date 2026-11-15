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

    public static List<String> compareJar(List<BridgeOverrideRef> refs, Range range, Map<String, Range> overrides,
                                          RefJars jars, Path bridgeClasses, ClassOrigins origins) {
        BridgeOverrideComparer comparer = new BridgeOverrideComparer();
        comparer.indexBranch(bridgeClasses);
        List<String> issues = new ArrayList<>();
        for (BridgeOverrideRef ref : refs) {
            Range refRange = ref.overrideKey == null ? range : overrides.get(ref.overrideKey);
            if (refRange == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }
            boolean floor = comparer.gameMatch(ref, jars.jar(refRange.floor), origins);
            boolean ceiling = comparer.gameMatch(ref, jars.jar(refRange.ceiling), origins);
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

    public static List<String> compareCode(List<BridgeOverrideRef> refs, Range range, Map<String, Range> overrides,
                                           Path gameDir, Path arcDir, Path bridgeClasses) {
        BridgeOverrideComparer comparer = new BridgeOverrideComparer();
        List<String> issues = new ArrayList<>();
        Map<String, SourceHierarchy> hierarchies = new HashMap<>();
        for (BridgeOverrideRef ref : refs) {
            Range refRange = ref.overrideKey == null ? range : overrides.get(ref.overrideKey);
            if (refRange == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }
            SourceHierarchy floor = hierarchies.computeIfAbsent(refRange.floor + "|" + refRange.arcFloor,
                    key -> new SourceHierarchy(gameDir, refRange.floor, arcDir,
                            refRange.arcFloor == null ? refRange.floor : refRange.arcFloor, bridgeClasses));
            SourceHierarchy ceiling = hierarchies.computeIfAbsent(refRange.ceiling + "|" + refRange.arcCeiling,
                    key -> new SourceHierarchy(gameDir, refRange.ceiling, arcDir,
                            refRange.arcCeiling == null ? refRange.ceiling : refRange.arcCeiling, bridgeClasses));
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

    private boolean gameMatch(BridgeOverrideRef ref, JarIndex jar, ClassOrigins origins) {
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
            if (info.member(ref.name, ref.descriptor) != null && isGame(name, origins))
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

    /** The layer the runtime classpath resolves the type in; only the game layer's presence changes across a range. */
    private boolean isGame(String name, ClassOrigins origins) {
        if (origins != null)
            return origins.of(name) == ClassOrigins.Layer.GAME;
        return !branchClasses.containsKey(name);
    }
}
