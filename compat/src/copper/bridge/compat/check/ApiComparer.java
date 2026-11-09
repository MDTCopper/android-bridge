package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.refs.ClassRef;
import copper.bridge.compat.sources.RefJars;

import java.util.*;

/**
 * Compares the declarations the branch references between the two ends of its range.
 *
 * <p>A reference compiled against the epoch must resolve at the floor too, so every floor declaration must also
 * exist at the ceiling. Additions at the ceiling are fine; a changed or removed floor declaration is an issue.
 * A reference from a class with {@code @CompatRange} uses that class's own range instead of the branch's.
 * Members inherited from a supertype the check does not read are skipped.
 */
public final class ApiComparer {
    /** Only the flags a compiled call depends on; a body added later does not break it. */
    private static final int ACCESS_MASK = 0x0001 | 0x0002 | 0x0004 | 0x0008; //public, private, protected, static

    private static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList(
            "ordinal", "name", "toString", "hashCode", "equals", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait"));

    private ApiComparer() {
    }

    public static List<String> compare(List<ClassRef> refs, Range branch, Map<String, Range> overrides,
                                       RefJars jars) {
        List<String> issues = new ArrayList<>();

        for (ClassRef ref : refs) {
            Range range = ref.overrideKey == null ? branch : overrides.get(ref.overrideKey);
            if (range == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }

            ClassInfo floor = jars.jar(range.floor).classInfo(ref.owner);
            if (floor == null) {
                issues.add("class missing at floor: " + ref.owner);
                continue;
            }
            ClassInfo ceiling = jars.jar(range.ceiling).classInfo(ref.owner);
            if (ceiling == null) {
                issues.add("class missing at ceiling: " + ref.owner);
                continue;
            }

            MemberInfo floorMember = floor.member(ref.name, ref.descriptor);
            if (floorMember == null) {
                if (OBJECT_METHODS.contains(ref.name) && ceiling.member(ref.name, ref.descriptor) == null)
                    continue;
                issues.add("member missing at floor: " + ref.owner + "." + ref.name + " " + ref.descriptor);
                continue;
            }

            MemberInfo ceilingMember = ceiling.member(ref.name, ref.descriptor);
            if (ceilingMember == null) {
                issues.add("declaration changed or removed: " + ref.owner + "." + ref.name + " " + ref.descriptor);
                continue;
            }
            if (!ref.name.equals("<init>")
                    && (floorMember.access & ACCESS_MASK) != (ceilingMember.access & ACCESS_MASK)) {
                issues.add("declaration changed: " + ref.owner + "." + ref.name + " " + ref.descriptor
                        + " access " + floorMember.access + " -> " + ceilingMember.access);
            }
        }
        return issues;
    }
}
