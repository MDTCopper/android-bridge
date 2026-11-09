package copper.bridge.compat.check;

import copper.bridge.compat.api.ClassInfo;
import copper.bridge.compat.refs.ArcReflectRef;
import copper.bridge.compat.sources.RefJars;

import java.util.*;

/** Checks that a field named to arc's {@code Reflect} exists at one end of the branch's range. */
public final class ArcReflectComparer {
    private ArcReflectComparer() {
    }

    public static List<String> compare(List<ArcReflectRef> refs, Range range, RefJars jars) {
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

    private static boolean exists(copper.bridge.compat.api.JarIndex jar, ArcReflectRef ref) {
        ClassInfo info = jar.classInfo(ref.owner);
        return info != null && info.hasMemberNamed(ref.member);
    }
}
