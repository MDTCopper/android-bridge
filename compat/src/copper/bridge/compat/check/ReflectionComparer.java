package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.refs.HandledRefs;
import copper.bridge.compat.refs.ReflectionRef;
import copper.bridge.compat.sources.RefJars;
import copper.bridge.compat.sources.SourceTree;
import org.objectweb.asm.Opcodes;

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
                                          RefJars jars, ClassOrigins origins) {
        return compareJar(refs, branch, overrides, jars, origins, null);
    }

    public static List<String> compareJar(List<ReflectionRef> refs, Range branch, Map<String, Range> overrides,
                                          RefJars jars, ClassOrigins origins, HandledRefs handled) {
        List<String> issues = new ArrayList<>();
        for (ReflectionRef ref : refs) {
            if (ref.kind == ReflectionRef.Kind.TYPE)
                continue;
            if (outsideGame(ref, origins)) {
                if (gameName(ref.owner) && (handled == null
                        || !handled.handles(ref.owner, ref.member, HandledRefs.Kind.REFLECTION)))
                    System.out.println("  warning: reflection target is not on either endpoint classpath: " + ref);
                continue;
            }
            Range range = ref.overrideKey == null ? branch : overrides.get(ref.overrideKey);
            if (range == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }
            JarIndex floorJar = jars.jar(range.floor);
            JarIndex ceilingJar = jars.jar(range.ceiling);
            boolean floorPresent = present(floorJar, ref);
            boolean ceilingPresent = present(ceilingJar, ref);
            if (!floorPresent && !ceilingPresent) {
                issues.add("reflection target is absent at both ends: " + ref + " (dead probe)");
                continue;
            }
            // Accessibility matters as much as presence: a private field at the floor justifies reflection even when
            // the same field is public at the ceiling.
            if (directly(floorJar, ref) && directly(ceilingJar, ref))
                issues.add("reflection target is public at both ends: " + ref + " (use it directly)");
        }
        return issues;
    }

    private static boolean gameName(String owner) {
        return owner.startsWith("arc/") || owner.startsWith("io/anuke/arc/")
                || owner.startsWith("mindustry/") || owner.startsWith("io/anuke/mindustry/");
    }

    private static boolean directly(JarIndex jar, ReflectionRef ref) {        ClassInfo info = jar.classInfo(ref.owner);
        if (info == null || (info.access & Opcodes.ACC_PUBLIC) == 0)
            return false;
        if (ref.member == null || "<init>".equals(ref.member))
            return true;
        for (MemberInfo member : info.members()) {
            if (member.name.equals(ref.member) && (member.access & Opcodes.ACC_PUBLIC) != 0)
                return true;
        }
        return false;
    }

    public static List<String> compareCode(List<ReflectionRef> refs, Range branch, Map<String, Range> overrides,
                                           Path gameDir, Path arcDir, ClassOrigins origins) {
        List<String> issues = new ArrayList<>();
        Map<String, SourceTree> trees = new HashMap<>();
        for (ReflectionRef ref : refs) {
            if (outsideGame(ref, origins))
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

    /**
     * Whether the target is not the game's: the bridge and the JDK are always present, so their presence does not
     * change across the range. Without the layered classpath the old bridge-internal rule stands.
     */
    private static boolean outsideGame(ReflectionRef ref, ClassOrigins origins) {
        if (origins != null)
            return origins.of(ref.owner) != ClassOrigins.Layer.GAME;
        return ref.owner.startsWith("copper/bridge/");
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
