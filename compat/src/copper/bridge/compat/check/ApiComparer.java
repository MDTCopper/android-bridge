package copper.bridge.compat.check;

import copper.bridge.compat.api.JavaApi;
import copper.bridge.compat.api.MemberSignature;
import copper.bridge.compat.refs.ClassRef;
import copper.bridge.compat.sources.Git;
import copper.bridge.compat.sources.SourceTree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares the declarations the branch references between the two ends of its range.
 *
 * <p>A reference compiled against the epoch must resolve at the floor too, so every floor declaration must also
 * exist at the ceiling. Additions at the ceiling are fine; a changed or removed floor declaration is an issue.
 * A reference from a class with {@code @CompatFloor} uses that class's own range instead of the branch's.
 * Members inherited from a supertype the check does not read are skipped, as is an implicit default constructor.
 */
public final class ApiComparer {
    private static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList(
            "ordinal", "name", "toString", "hashCode", "equals", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait"));

    private ApiComparer() {
    }

    public static List<String> compare(List<ClassRef> refs, Range branch, Map<String, Range> overrides,
                                       Path gameClone, Path arcClone) {
        List<String> issues = new ArrayList<>();
        Map<String, SourceTree> trees = new HashMap<>();
        Map<String, JavaApi.Type> types = new HashMap<>();

        for (ClassRef ref : refs) {
            Range range = ref.overrideKey == null ? branch : overrides.get(ref.overrideKey);
            if (range == null) {
                issues.add("no range for @CompatRange " + ref.overrideKey + " on " + ref.sourceClass);
                continue;
            }
            boolean arc = ref.isArc();
            String floor = arc ? range.arcFloor : range.gameFloor;
            String ceiling = arc ? range.arcCeiling : range.gameCeiling;
            Path floorClone = arc && !range.arcFloorMerged ? arcClone : gameClone;
            Path ceilingClone = arc && !range.arcCeilingMerged ? arcClone : gameClone;

            String classPath = ref.owner.contains("$")
                    ? ref.owner.substring(0, ref.owner.indexOf('$'))
                    : ref.owner;
            String outerName = classPath.substring(classPath.lastIndexOf('/') + 1);
            String nestedName = ref.owner.contains("$")
                    ? ref.owner.substring(ref.owner.lastIndexOf('$') + 1)
                    : null;

            String floorKey = floorClone + "|" + floor + "|" + arc;
            String ceilingKey = ceilingClone + "|" + ceiling + "|" + arc;
            SourceTree floorTree = trees.computeIfAbsent(floorKey,
                    key -> new SourceTree(new Git(floorClone.toString()), floor, arc));
            SourceTree ceilingTree = trees.computeIfAbsent(ceilingKey,
                    key -> new SourceTree(new Git(ceilingClone.toString()), ceiling, arc));

            String floorPath = floorTree.resolve(classPath);
            String ceilingPath = ceilingTree.resolve(classPath);
            if (floorPath == null) {
                issues.add("class missing at floor: " + ref.owner);
                continue;
            }
            if (ceilingPath == null) {
                issues.add("class missing at ceiling: " + ref.owner);
                continue;
            }

            JavaApi.Type floorType = types.computeIfAbsent(floorKey + "|" + classPath + "|" + nestedName,
                    key -> JavaApi.type(floorTree.source(floorPath), floorPath, outerName, nestedName));
            JavaApi.Type ceilingType = types.computeIfAbsent(ceilingKey + "|" + classPath + "|" + nestedName,
                    key -> JavaApi.type(ceilingTree.source(ceilingPath), ceilingPath, outerName, nestedName));
            if (floorType == null || ceilingType == null) {
                issues.add("type not found in source: " + ref.owner);
                continue;
            }
            if (!floorType.kind.equals(ceilingType.kind)) {
                issues.add("type kind changed: " + ref.owner + " " + floorType.kind + " -> " + ceilingType.kind);
                continue;
            }

            int arity = arity(ref.descriptor);
            Set<MemberSignature> floorSignatures = withArity(JavaApi.members(floorType.tree, ref.name), arity);
            Set<MemberSignature> ceilingSignatures = withArity(JavaApi.members(ceilingType.tree, ref.name), arity);

            if (floorSignatures.isEmpty()) {
                if (ref.name.equals("<init>") && arity == 0)
                    continue;
                if (OBJECT_METHODS.contains(ref.name) && ceilingSignatures.isEmpty())
                    continue;
                issues.add("member missing at floor: " + ref.owner + "." + ref.name + " " + ref.descriptor);
                continue;
            }
            if (ceilingSignatures.isEmpty())
                continue;

            for (MemberSignature signature : floorSignatures) {
                if (!ceilingSignatures.contains(signature)) {
                    issues.add("declaration changed or removed: " + ref.owner + "." + ref.name
                            + " floor=" + signature.text);
                    break;
                }
            }
        }
        return issues;
    }

    /** Parameter count for a method descriptor, -1 for a field. */
    private static int arity(String descriptor) {
        if (descriptor.isEmpty() || descriptor.charAt(0) != '(')
            return -1;
        int count = 0;
        for (int i = 1; i < descriptor.length(); i++) {
            char c = descriptor.charAt(i);
            if (c == ')')
                break;
            if (c == '[')
                continue;
            if (c == 'L') {
                while (i < descriptor.length() && descriptor.charAt(i) != ';')
                    i++;
                count++;
            } else {
                count++;
            }
        }
        return count;
    }

    private static Set<MemberSignature> withArity(Set<MemberSignature> signatures, int arity) {
        return signatures.stream()
                .filter(signature -> arity < 0 ? signature.arity < 0 : signature.arity == arity)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
