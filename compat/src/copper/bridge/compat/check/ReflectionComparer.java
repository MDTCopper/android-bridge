package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.refs.ReflectionRef;
import copper.bridge.compat.sources.RefJars;
import copper.bridge.compat.sources.SourceTree;

import java.nio.file.Path;
import java.util.*;

/**
 * Checks the reflection targets a branch names.
 *
 * <p>A reflection target is only justified when its presence changes inside the branch's range: present at one
 * end and absent at the other. Present at both ends means a direct call would do, absent at both means the probe
 * is dead. A member is looked up by name because reflection does not carry a descriptor.
 */
public final class ReflectionComparer {

    private ReflectionComparer() {
    }

    public static List<String> compareJar(List<ReflectionRef> refs, Range branch, Map<String, Range> overrides,
                                          RefJars jars) {
        List<String> issues = new ArrayList<>();
        for (ReflectionRef ref : refs) {
            if (ref.owner.startsWith("copper/bridge/"))
                continue; // bridge-internal: loaded by name so an old epoch never links its newer game types
            Range range = ref.overrideKey == null ? branch : overrides.get(ref.overrideKey);
            if (range == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }
            boolean floor = present(jars.jar(range.floor), ref);
            boolean ceiling = present(jars.jar(range.ceiling), ref);
            if (floor == ceiling)
                issues.add(message(ref, floor));
        }
        return issues;
    }

    public static List<String> compareCode(List<ReflectionRef> refs, Range branch, Map<String, Range> overrides,
                                           Path gameDir, Path arcDir) {
        List<String> issues = new ArrayList<>();
        Map<String, SourceTree> trees = new HashMap<>();
        for (ReflectionRef ref : refs) {
            if (ref.owner.startsWith("copper/bridge/"))
                continue;
            Range range = ref.overrideKey == null ? branch : overrides.get(ref.overrideKey);
            if (range == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }
            boolean arc = ref.owner.startsWith("arc/") || ref.owner.startsWith("io/anuke/arc/");
            String floor = arc ? range.arcFloor : range.floor;
            String ceiling = arc ? range.arcCeiling : range.ceiling;
            Path clone = arc ? arcDir : gameDir;
            SourceTree floorTree = trees.computeIfAbsent(clone + "|" + floor + "|" + arc,
                    key -> new SourceTree(new copper.bridge.compat.sources.Git(clone.toString()), floor, arc));
            SourceTree ceilingTree = trees.computeIfAbsent(clone + "|" + ceiling + "|" + arc,
                    key -> new SourceTree(new copper.bridge.compat.sources.Git(clone.toString()), ceiling, arc));
            boolean floorPresent = present(floorTree, ref);
            boolean ceilingPresent = present(ceilingTree, ref);
            if (floorPresent == ceilingPresent)
                issues.add(message(ref, floorPresent));
        }
        return issues;
    }

    private static String message(ReflectionRef ref, boolean present) {
        return present
                ? "reflection target is present at both ends: " + ref + " (use it directly)"
                : "reflection target is absent at both ends: " + ref + " (dead probe)";
    }

    private static boolean present(JarIndex jar, ReflectionRef ref) {
        ClassInfo info = jar.classInfo(ref.owner);
        if (info == null)
            return false;
        if (ref.member == null || "<init>".equals(ref.member))
            return true;
        return info.hasMemberNamed(ref.member);
    }

    private static boolean present(SourceTree tree, ReflectionRef ref) {
        String path = tree.resolve(ref.owner);
        if (path == null)
            return false;
        if (ref.member == null || "<init>".equals(ref.member))
            return true;
        String outer = ref.owner.contains("$")
                ? ref.owner.substring(0, ref.owner.indexOf('$'))
                : ref.owner;
        String nested = ref.owner.contains("$")
                ? ref.owner.substring(ref.owner.lastIndexOf('$') + 1)
                : null;
        String name = outer.substring(outer.lastIndexOf('/') + 1);
        JavaApi.Type type = JavaApi.type(tree.source(path), path, name, nested);
        return type != null && !JavaApi.members(type.tree, ref.member).isEmpty();
    }
}
