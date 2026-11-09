package copper.bridge.compat.check;

import copper.bridge.compat.api.*;
import copper.bridge.compat.refs.ClassRef;
import copper.bridge.compat.sources.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares the declarations the branch references between the two ends of its range, reading the game and arc
 * sources from their git clones instead of built jars.
 *
 * <p>Same rule as the jar check: a reference compiled against the epoch must resolve at both ends with the same
 * declaration. A class annotated with {@code @CompatRange} uses that class's own range. Members inherited from a
 * supertype the check does not read are skipped, as is an implicit default constructor.
 */
public final class SourceComparer {
    private static final Set<String> OBJECT_METHODS = new HashSet<>(Arrays.asList(
            "ordinal", "name", "toString", "hashCode", "equals", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait"));

    private SourceComparer() {
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
            String floor = arc ? range.arcFloor : range.floor;
            String ceiling = arc ? range.arcCeiling : range.ceiling;
            Path floorClone = arc ? arcClone : gameClone;
            Path ceilingClone = arc ? arcClone : gameClone;

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
            if (ref.name.equals("<init>") && arity == 0) {
                // an implicit default constructor has no MethodTree at all
                continue;
            }
            if (isImplicitEnumMethod(ref.name, floorType)) {
                // values()/valueOf() are compiler-generated and never appear in the source tree
                continue;
            }
            Set<MemberSignature> floorSignatures = matching(
                    withArity(JavaApi.members(floorType.tree, ref.name), arity), ref.descriptor);
            Set<MemberSignature> ceilingSignatures = matching(
                    withArity(JavaApi.members(ceilingType.tree, ref.name), arity), ref.descriptor);

            if (floorSignatures.isEmpty()) {
                if (OBJECT_METHODS.contains(ref.name) && ceilingSignatures.isEmpty())
                    continue;
                issues.add("member missing at floor: " + ref.owner + "." + ref.name + " " + ref.descriptor);
                continue;
            }
            if (ceilingSignatures.isEmpty()) {
                issues.add("member missing at ceiling: " + ref.owner + "." + ref.name + " " + ref.descriptor);
                continue;
            }

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

    /** {@code values()}/{@code valueOf()} are generated by the compiler and never written in the source. */
    private static boolean isImplicitEnumMethod(String name, JavaApi.Type type) {
        return type.kind.equals("ENUM") && (name.equals("values") || name.equals("valueOf"));
    }

    /** Parameter count for a method descriptor, -1 for a field. */
    private static int arity(String descriptor) {        if (descriptor.isEmpty() || descriptor.charAt(0) != '(')
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

    /**
     * Narrows a method's overloads to the one the reference's descriptor names. The descriptor is erased and the
     * source text is not, so the comparison is on simple type names with generics stripped. No match keeps the whole
     * set, which is the conservative behaviour for anything the matching cannot express (varargs, type variables).
     */
    private static Set<MemberSignature> matching(Set<MemberSignature> signatures, String descriptor) {
        List<String> wanted = descriptorParams(descriptor);
        if (wanted.isEmpty())
            return signatures;

        Set<MemberSignature> matched = signatures.stream()
                .filter(signature -> signature.params.size() == wanted.size())
                .filter(signature -> {
                    for (int i = 0; i < wanted.size(); i++) {
                        if (!simple(signature.params.get(i)).equals(wanted.get(i)))
                            return false;
                    }
                    return true;
                })
                .collect(Collectors.toCollection(HashSet::new));
        return matched.isEmpty() ? signatures : matched;
    }

    /** Parameter type names from a method descriptor, as the source would spell them; empty for a field. */
    private static List<String> descriptorParams(String descriptor) {
        List<String> params = new ArrayList<>();
        if (descriptor.isEmpty() || descriptor.charAt(0) != '(')
            return params;

        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            int dimensions = 0;
            while (descriptor.charAt(i) == '[') {
                dimensions++;
                i++;
            }
            String name;
            if (descriptor.charAt(i) == 'L') {
                int end = descriptor.indexOf(';', i);
                String internal = descriptor.substring(i + 1, end);
                int separator = Math.max(internal.lastIndexOf('/'), internal.lastIndexOf('$'));
                name = separator < 0 ? internal : internal.substring(separator + 1);
                i = end + 1;
            } else {
                name = primitive(descriptor.charAt(i));
                i++;
            }
            StringBuilder text = new StringBuilder(name);
            for (int d = 0; d < dimensions; d++)
                text.append("[]");
            params.add(text.toString());
        }
        return params;
    }

    private static String simple(String type) {
        String text = type.trim();
        int generic = text.indexOf('<');
        if (generic >= 0)
            text = text.substring(0, generic).trim();
        int dimensions = 0;
        while (text.endsWith("[]")) {
            dimensions++;
            text = text.substring(0, text.length() - 2).trim();
        }
        int separator = Math.max(text.lastIndexOf('.'), text.lastIndexOf('$'));
        if (separator >= 0)
            text = text.substring(separator + 1);
        StringBuilder full = new StringBuilder(text);
        for (int d = 0; d < dimensions; d++)
            full.append("[]");
        return full.toString();
    }

    private static String primitive(char descriptor) {
        switch (descriptor) {
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case 'V': return "void";
            default: return String.valueOf(descriptor);
        }
    }
}
