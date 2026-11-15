package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.refs.TypeDep;
import copper.bridge.compat.sources.RefJars;

import java.util.*;

/**
 * Checks the game and arc classes a branch names in its hierarchy, descriptors, annotations and class literals.
 * A type present at one end of a range and absent at the other is a linkage change; the member checks do not see it.
 */
public final class TypeComparer {

    private TypeComparer() {
    }

    public static List<String> compareJar(List<TypeDep> deps, Range branch, Map<String, Range> overrides,
                                          RefJars jars) {
        List<String> issues = new ArrayList<>();
        for (TypeDep dep : deps) {
            if (!gameName(dep.owner) || dep.owner.equals(dep.sourceClass))
                continue;
            Range range = dep.overrideKey == null ? branch : overrides.get(dep.overrideKey);
            if (range == null) {
                issues.add("no range for @CompatRange " + dep.overrideKey + " on " + dep.sourceClass);
                continue;
            }
            boolean floor = jars.jar(range.floor).classInfo(dep.owner) != null;
            boolean ceiling = jars.jar(range.ceiling).classInfo(dep.owner) != null;
            if (!floor || !ceiling)
                issues.add("type is " + (floor ? "missing at " + range.ceiling : "missing at " + range.floor)
                        + ": " + dep.owner + " (from " + dep.sourceClass + ")");
        }
        return issues;
    }

    private static boolean gameName(String owner) {
        return owner.startsWith("arc/") || owner.startsWith("io/anuke/arc/")
                || owner.startsWith("mindustry/") || owner.startsWith("io/anuke/mindustry/");
    }
}
