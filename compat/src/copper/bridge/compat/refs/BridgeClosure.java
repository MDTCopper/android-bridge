package copper.bridge.compat.refs;

import java.nio.file.*;
import java.util.*;

/**
 * Follows a branch's references into earlier bridge branches.
 *
 * <p>When {@code vN} uses a class from {@code vM} (M &lt; N), that class's own references must also hold across
 * {@code vN}'s range: its game/arc symbols are checked at the user's ends, and its bridge references are followed
 * further down. A forward reference is an error because the version table never selects a newer branch for an older
 * game.
 */
public final class BridgeClosure {

    public static final class Result {
        public final List<ClassRef> refs = new ArrayList<>();
        public final List<ReflectionRef> reflections = new ArrayList<>();
        public final List<String> issues = new ArrayList<>();
    }

    private BridgeClosure() {
    }

    public static Result expand(Path bridge, Map<Integer, Path> branchDirs, int version) {
        Result result = new Result();
        Map<String, ClassRef> refs = new LinkedHashMap<>();
        Map<String, ReflectionRef> reflections = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        java.util.List<BridgeRef> initial = ClassRefs.readBridge(bridge);
        Deque<BridgeRef> queue = new ArrayDeque<>(initial);

        while (!queue.isEmpty()) {
            BridgeRef ref = queue.removeFirst();
            // a branch's own classes are already checked directly; only refs that cross down are followed
            if (ref.branch == version)
                continue;
            if (ref.branch > version) {
                result.issues.add("bridge reference is not from a lower branch: " + ref.owner + " used by "
                        + ref.sourceClass);
                continue;
            }
            Path dir = branchDirs.get(ref.branch);
            if (dir == null) {
                result.issues.add("no classes staged for bridge-v" + ref.branch + " (needed by " + ref.owner + ")");
                continue;
            }
            if (!visited.add(ref.owner))
                continue;

            Path classFile = dir.resolve(ref.owner + ".class");
            if (!Files.isRegularFile(classFile)) {
                result.issues.add("class missing in bridge-v" + ref.branch + ": " + ref.owner);
                continue;
            }

            java.util.List<ClassRef> targetRefs = ClassRefs.readClass(classFile, ref.owner);
            for (ClassRef used : targetRefs) {
                if (used.isGameOrArc()) {
                    ClassRef plain = new ClassRef(used.sourceClass, used.owner, used.name, used.descriptor, null);
                    refs.putIfAbsent(plain.key(), plain);
                } else {
                    int usedBranch = BridgeRef.branchOf(used.owner);
                    if (usedBranch > 0)
                        queue.addLast(new BridgeRef(used.sourceClass, used.owner, used.name, used.descriptor,
                                usedBranch));
                }
            }
            for (ReflectionRef reflected : ReflectionRefs.readClass(classFile, ref.owner)) {
                ReflectionRef plain = new ReflectionRef(reflected.sourceClass, reflected.owner, reflected.member,
                        reflected.kind, null);
                reflections.putIfAbsent(plain.key(), plain);
            }
        }
        result.refs.addAll(refs.values());
        result.reflections.addAll(reflections.values());
        return result;
    }
}
