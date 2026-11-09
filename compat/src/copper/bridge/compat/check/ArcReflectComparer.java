package copper.bridge.compat.check;

import copper.bridge.compat.api.ClassInfo;
import copper.bridge.compat.api.JavaApi;
import copper.bridge.compat.sources.Git;
import copper.bridge.compat.sources.SourceTree;
import java.nio.file.Path;
import copper.bridge.compat.refs.ArcReflectRef;
import copper.bridge.compat.sources.RefJars;

import java.util.*;

/** Checks that a field named to arc's {@code Reflect} exists at one end of the branch's range. */
public final class ArcReflectComparer {
    private ArcReflectComparer() {
    }

    public static List<String> compareJar(List<ArcReflectRef> refs, Range range, RefJars jars) {
        List<String> issues = new ArrayList<>();
        for (ArcReflectRef ref : refs) {
            boolean floor = exists(jars.jar(range.floor), ref);
            boolean ceiling = exists(jars.jar(range.ceiling), ref);
            if (!floor && !ceiling)
                issues.add("arc Reflect target is deadcode: " + ref.owner + "." + ref.member
                        + " is declared at neither end");
        }
        return issues;
    }

    public static List<String> compareCode(List<ArcReflectRef> refs, Range range, Path gameDir, Path arcDir) {
        List<String> issues = new ArrayList<>();
        SourceTree gameFloor = new SourceTree(new Git(gameDir.toString()), range.floor, false);
        SourceTree gameCeiling = new SourceTree(new Git(gameDir.toString()), range.ceiling, false);
        SourceTree arcFloor = new SourceTree(new Git(arcDir.toString()),
                range.arcFloor == null ? range.floor : range.arcFloor, true);
        SourceTree arcCeiling = new SourceTree(new Git(arcDir.toString()),
                range.arcCeiling == null ? range.ceiling : range.arcCeiling, true);
        for (ArcReflectRef ref : refs) {
            boolean floor = exists(gameFloor, ref) || exists(arcFloor, ref);
            boolean ceiling = exists(gameCeiling, ref) || exists(arcCeiling, ref);
            if (!floor && !ceiling)
                issues.add("arc Reflect target is deadcode: " + ref.owner + "." + ref.member
                        + " is declared at neither end");
        }
        return issues;
    }

    private static boolean exists(SourceTree tree, ArcReflectRef ref) {
        String path = tree.resolve(ref.owner);
        if (path == null)
            return false;
        String outer = ref.owner.contains("$") ? ref.owner.substring(0, ref.owner.indexOf('$')) : ref.owner;
        String outerName = outer.substring(outer.lastIndexOf('/') + 1);
        String nested = ref.owner.contains("$") ? ref.owner.substring(ref.owner.lastIndexOf('$') + 1) : null;
        JavaApi.Type type = JavaApi.type(tree.source(path), path, outerName, nested);
        return type != null && !JavaApi.members(type.tree, ref.member).isEmpty();
    }

    private static boolean exists(copper.bridge.compat.api.JarIndex jar, ArcReflectRef ref) {
        ClassInfo info = jar.classInfo(ref.owner);
        return info != null && info.hasMemberNamed(ref.member);
    }
}
