package copper.bridge.gen.binding;

import copper.bridge.gen.Names;
import copper.bridge.gen.Output;
import copper.bridge.gen.Packages;
import copper.bridge.gen.TypeRef;
import copper.bridge.gen.Vocabulary;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;

/**
 * Reads the two binding annotations and writes every registration table, the list of them, and the calls
 * native makes back into Java.
 *
 * <p>Both directions are read from the same declarations, so names, descriptors and entries cannot
 * drift apart. They are written once, after all rounds: some belong to classes another pass generates,
 * and writing early would leave half a set on disk for the check option to compare against.</p>
 */
@SupportedAnnotationTypes({BindingProcessor.NATIVE, BindingProcessor.USED_BY_NATIVE})
@SupportedOptions({Output.OUTPUT_OPTION})
public final class BindingProcessor extends AbstractProcessor {
    static final String NATIVE = Packages.ANNOTATIONS + ".Native";
    static final String USED_BY_NATIVE = Packages.ANNOTATIONS + ".UsedByNative";

    /** Each class's natives, keyed by its qualified name. */
    private final Map<String, List<Forward>> forwards = new TreeMap<>();
    /** Table symbols already taken; two classes cannot claim one. */
    private final Set<String> tableNames = new LinkedHashSet<>();
    /** Keyed by the name the generated entry carries. */
    private final Map<String, Reverse> reverses = new LinkedHashMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (!round.processingOver()) {
            collect(round);
            return false;
        }

        if (forwards.isEmpty() && reverses.isEmpty())
            return false;
        try {
            BindingGen.generate(this);
        } catch (IOException e) {
            error(null, "cannot write the generated binding layer: " + e);
        }
        return false;
    }

    // --- collecting ---------------------------------------------------------

    private void collect(RoundEnvironment round) {
        // A native with no entry binds nothing, and the symptom is a call that fails much later - or never,
        // for a method nothing calls.
        for (Element root : round.getRootElements())
            requireEntry(root);

        for (Element element : annotated(round, NATIVE))
            collectForward(element);
        for (Element element : annotated(round, USED_BY_NATIVE))
            collectReverse(element);
    }

    private List<Element> annotated(RoundEnvironment round, String annotation) {
        TypeElement marker = processingEnv.getElementUtils().getTypeElement(annotation);
        if (marker == null)
            return List.of();
        List<Element> found = new ArrayList<>(round.getElementsAnnotatedWith(marker));
        found.sort(Comparator.comparing(Element::toString));
        return found;
    }

    /** Recurses into nested classes, reporting every native that names no entry. */
    private void requireEntry(Element element) {
        for (Element member : element.getEnclosedElements()) {
            if (member.getKind().isClass() || member.getKind().isInterface()) {
                requireEntry(member);
                continue;
            }
            if (member.getKind() != ElementKind.METHOD)
                continue;
            ExecutableElement method = (ExecutableElement) member;
            if (!method.getModifiers().contains(Modifier.NATIVE) || hasAnnotation(method, NATIVE))
                continue;
            error(method, "'" + method.getSimpleName() + "' is native and names no entry; add @"
                    + simple(NATIVE) + "(\"<namespace>::<function>\") so the table can be written from it");
        }
    }

    private void collectForward(Element element) {
        if (element.getKind() != ElementKind.METHOD) {
            error(element, "@" + simple(NATIVE) + " names the entry of a native method");
            return;
        }

        ExecutableElement method = (ExecutableElement) element;
        if (!method.getModifiers().contains(Modifier.NATIVE)) {
            error(method, "'" + method.getSimpleName() + "' carries @" + simple(NATIVE)
                    + " and is not native, so there is nothing to bind");
            return;
        }

        List<TypeRef> params = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            TypeRef type = NativeType.of(parameter.asType());
            if (type == null) {
                error(parameter, "the type of '" + parameter.getSimpleName() + "' is outside what a native"
                        + " declaration may name; allowed are: " + Vocabulary.names() + ", java.lang.Object");
                return;
            }
            params.add(type);
        }

        TypeRef result = NativeType.of(method.getReturnType());
        if (result == null) {
            error(method, "the return type of '" + method.getSimpleName() + "' is outside what a native"
                    + " declaration may name; allowed are: " + Vocabulary.names() + ", java.lang.Object");
            return;
        }

        String entry = annotationString(method, NATIVE, "value", "");
        int split = entry.lastIndexOf("::");
        String entryNamespace = "copper::bridge";
        String entryName = entry;
        if (split >= 0) {
            entryNamespace = "copper::bridge::" + entry.substring(0, split);
            entryName = entry.substring(split + 2);
        }
        if (!entryName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            error(method, "the entry '" + entry + "' does not end in a C++ function name");
            return;
        }

        TypeElement owner = (TypeElement) method.getEnclosingElement();
        String ownerName = owner.getQualifiedName().toString();
        Forward forward = new Forward(ownerName, ownerName.replace('.', '/'), owner.getSimpleName().toString(),
                method.getSimpleName().toString(), descriptor(params, result), entry, entryNamespace, entryName,
                method.getModifiers().contains(Modifier.STATIC) ? "jclass" : "jobject", result, params);

        List<Forward> rows = forwards.get(ownerName);
        if (rows == null) {
            // A table is named after its class's simple name, so two classes of that name want one symbol.
            if (!tableNames.add(forward.table())) {
                error(method, ownerName + " wants the table " + forward.table() + ", which another class"
                        + " already claimed; a table is named after the simple name of its class");
                return;
            }
            rows = new ArrayList<>();
            forwards.put(ownerName, rows);
        }
        rows.add(forward);
    }

    private void collectReverse(Element element) {
        if (element.getKind() != ElementKind.METHOD) {
            error(element, "native reaches for a method by name; this is a "
                    + element.getKind().toString().toLowerCase(Locale.ROOT)
                    + ", and the generated lookup does not know how to reach one");
            return;
        }

        ExecutableElement method = (ExecutableElement) element;
        if (!method.getModifiers().contains(Modifier.STATIC)) {
            error(method, "'" + method.getSimpleName() + "' is not static, and a lookup that has no instance"
                    + " can only reach a static member");
            return;
        }

        List<TypeRef> params = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            TypeRef type = NativeType.of(parameter.asType());
            if (type == null) {
                error(parameter, "the type of '" + parameter.getSimpleName() + "' is outside the bridge's"
                        + " vocabulary; allowed are: " + Vocabulary.names() + ", java.lang.Object");
                return;
            }
            params.add(type);
        }

        TypeRef result = NativeType.of(method.getReturnType());
        if (result == null) {
            error(method, "the return type of '" + method.getSimpleName() + "' is outside the bridge's"
                    + " vocabulary; allowed are: " + Vocabulary.names() + ", java.lang.Object");
            return;
        }

        String side = annotationString(method, USED_BY_NATIVE, "side", "BOTH");
        if (!"BOTH".equals(side) && !"ART".equals(side) && !"JVM".equals(side)) {
            error(method, "'" + side + "' is not one of BOTH, ART and JVM");
            return;
        }
        boolean detaches = Boolean.parseBoolean(annotationString(method, USED_BY_NATIVE, "detaches", "false"));
        if (detaches && !result.isVoid) {
            // The answer is a reference this thread owns, released when the attachment goes back.
            error(method, "'" + method.getSimpleName() + "' answers with a value and gives its attachment"
                    + " back before the call returns; the answer would be released with the thread's"
                    + " references, so only a call that answers with nothing can do that");
            return;
        }

        TypeElement owner = (TypeElement) method.getEnclosingElement();
        String ownerName = owner.getQualifiedName().toString();
        String entry = Names.capitalize(method.getSimpleName().toString());

        Reverse other = reverses.get(entry);
        if (other != null) {
            // The same declaration seen again in a later round is not a second member.
            if (other.owner.equals(ownerName) && other.method.equals(method.getSimpleName().toString()))
                return;
            error(method, "'" + method.getSimpleName() + "' and '" + other.method + "' of " + other.owner
                    + " both want the generated name " + entry + "; a generated call is named after the"
                    + " member, so two members cannot share one");
            return;
        }

        reverses.put(entry, new Reverse(ownerName, ownerName.replace('.', '/'),
                method.getSimpleName().toString(), descriptor(params, result), entry, side, detaches, params,
                result));
    }

    // --- helpers ------------------------------------------------------------

    private static boolean hasAnnotation(Element element, String name) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(name))
                return true;
        }
        return false;
    }

    /**
     * One annotation element, as a string. Read by name rather than through a class reference: this pass
     * is compiled before the bridge is, so the annotation is a name to it too. An enum constant arrives as
     * the constant itself, which is why its simple name is what is returned.
     */
    private String annotationString(Element element, String annotation, String name, String fallback) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(annotation))
                continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals(name))
                    continue;
                Object value = entry.getValue().getValue();
                if (value instanceof VariableElement)
                    return ((VariableElement) value).getSimpleName().toString();
                return String.valueOf(value);
            }
        }
        return fallback;
    }

    private static String simple(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private static String descriptor(List<TypeRef> params, TypeRef result) {
        StringBuilder text = new StringBuilder("(");
        for (TypeRef param : params)
            text.append(param.descriptor);
        return text.append(')').append(result.descriptor).toString();
    }

    // --- what the generator reads -------------------------------------------

    /** Sorted so the generated file does not move between two equal runs. */
    Map<String, List<Forward>> forwards() {
        Map<String, List<Forward>> sorted = new TreeMap<>();
        for (Map.Entry<String, List<Forward>> entry : forwards.entrySet()) {
            List<Forward> rows = new ArrayList<>(entry.getValue());
            rows.sort(Comparator.comparing(row -> row.method));
            sorted.put(entry.getKey(), rows);
        }
        return sorted;
    }

    /** Sorted for the same reason as {@link #forwards()}: a generated file that does not move. */
    List<Reverse> reverses() {
        List<Reverse> sorted = new ArrayList<>(reverses.values());
        sorted.sort(Comparator.comparing((Reverse member) -> member.owner).thenComparing(member -> member.entry));
        return sorted;
    }

    /** The reverse members that need a handle: a member of both VMs looks its own class up per call. */
    List<Reverse> slotted() {
        List<Reverse> slotted = new ArrayList<>();
        for (Reverse member : reverses()) {
            if (!member.bothSides())
                slotted.add(member);
        }
        return slotted;
    }

    void writeCpp(String name, String content) throws IOException {
        Output.writeCpp(processingEnv, name, content);
    }

    void error(Element element, String message) {
        Output.error(processingEnv, element, message);
    }
}
