package copper.bridge.gen.bus;

import copper.bridge.gen.Names;
import copper.bridge.gen.Output;
import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
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
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

/**
 * Reads the bridge's call declarations and writes both sides of the bus: the Java entry points and the native
 * tables and stubs. Declarations in core are the only source, so no table is maintained by hand, and all six
 * handler annotations are known even though only three are written today. A declaration that cannot be honoured
 * is a compile error here.
 */
@SupportedAnnotationTypes({BusProcessor.ART_POST, BusProcessor.ART_DIRECT, BusProcessor.ART_EVENT,
        BusProcessor.JVM_POST, BusProcessor.JVM_DIRECT, BusProcessor.JVM_EVENT})
@SupportedOptions({Output.OUTPUT_OPTION})
public final class BusProcessor extends AbstractProcessor {
    static final String ART_POST = Packages.ANNOTATIONS + ".ArtPostHandler";
    static final String ART_DIRECT = Packages.ANNOTATIONS + ".ArtDirectHandler";
    static final String ART_EVENT = Packages.ANNOTATIONS + ".ArtEventHandler";
    static final String JVM_POST = Packages.ANNOTATIONS + ".JvmPostHandler";
    static final String JVM_DIRECT = Packages.ANNOTATIONS + ".JvmDirectHandler";
    static final String JVM_EVENT = Packages.ANNOTATIONS + ".JvmEventHandler";
    static final String ACCEPT_OBJECT_ANSWER = Packages.ANNOTATIONS + ".AcceptObjectAnswer";

    /** The largest object-parameter count a boxed payload may have: its header counts in a byte. */
    private static final int MAX_BOXED = 255;

    private static final class Handler {
        final String annotation;
        final Side owner;
        final Channel channel;

        Handler(String annotation, Side owner, Channel channel) {
            this.annotation = annotation;
            this.owner = owner;
            this.channel = channel;
        }
    }
    private static final List<Handler> HANDLERS = List.of(
            new Handler(ART_POST, Side.ART, Channel.POST),
            new Handler(ART_DIRECT, Side.ART, Channel.DIRECT),
            new Handler(ART_EVENT, Side.ART, Channel.EVENT),
            new Handler(JVM_POST, Side.JVM, Channel.POST),
            new Handler(JVM_DIRECT, Side.JVM, Channel.DIRECT),
            new Handler(JVM_EVENT, Side.JVM, Channel.EVENT));

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> holderNames = new LinkedHashSet<>();
    private boolean generated;

    private int maxArgs;
    private int maxParams;
    private int maxRefs;
    private int maxBoxed;

    List<Row> rows() {
        return rows;
    }

    int maxArgs() {
        return maxArgs;
    }

    int maxParams() {
        return maxParams;
    }

    int maxRefs() {
        return maxRefs;
    }

    int maxBoxed() {
        return maxBoxed;
    }

    public static String callClass(Side side) {
        return side == Side.JVM ? "JvmCall" : "ArtCall";
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (generated || round.processingOver())
            return false;

        List<Handler> present = new ArrayList<>();
        for (Handler handler : HANDLERS) {
            if (annotation(handler.annotation) != null)
                present.add(handler);
        }
        if (present.isEmpty())
            return false;

        List<ExecutableElement> declared = new ArrayList<>();
        for (Handler handler : present)
            declared.addAll(methods(round.getElementsAnnotatedWith(annotation(handler.annotation))));
        if (declared.isEmpty())
            return false;

        generated = true;
        try {
            for (Handler handler : present) {
                for (ExecutableElement method : methods(round.getElementsAnnotatedWith(annotation(handler.annotation))))
                    collect(handler, method);
            }
            assign();
            JavaGen.generate(this);
            CppGen.generate(this);
        } catch (IOException e) {
            error(null, "cannot write the generated bus: " + e);
        }
        return true;
    }

    //region collecting

    private void collect(Handler handler, ExecutableElement method) {
        TypeElement owner = owner(method);
        if (handler.channel == Channel.EVENT) {
            if (owner.getKind() != ElementKind.CLASS && owner.getKind() != ElementKind.INTERFACE) {
                error(method, "an event handler must be a method of a class or an interface");
                return;
            }
        } else if (owner.getKind() != ElementKind.CLASS) {
            error(method, "a handler of the " + handler.channel.cpp.toLowerCase(Locale.ROOT)
                    + " channel must be a method of a class, not of " + owner.getKind());
            return;
        }
        requireCallable(method);

        Row row = new Row();
        row.name = method.getSimpleName().toString();
        row.channel = handler.channel;
        row.ownerSide = handler.owner;
        row.callSide = handler.owner.caller();
        row.owner = owner.getQualifiedName().toString();
        row.method = row.name;
        row.isStatic = method.getModifiers().contains(Modifier.STATIC);
        row.acceptObjectAnswer = hasAnnotation(method, ACCEPT_OBJECT_ANSWER);
        if (!fill(row, method))
            return;

        if (handler.channel == Channel.DIRECT) {
            if (row.isStatic) {
                error(method, "a direct handler must be an instance method: the native table for a direct row"
                        + " carries no static flag");
                return;
            }
            rows.add(row);
            return;
        }

        if (handler.channel == Channel.EVENT) {
            if (!row.result.isVoid) {
                error(method, "an event cannot answer: '" + row.name + "' returns a value, and nothing travels"
                        + " back on this channel");
                return;
            }
            rows.add(row);
            return;
        }

        collectPost(handler, method, row);
    }

    private void collectPost(Handler handler, ExecutableElement method, Row row) {
        List<String> outcomes = callbackStrings(method, handler.annotation);
        if (outcomes.isEmpty()) {
            rows.add(row);
            if (!row.result.isVoid) {
                // A posted call that returns a value: the caller waits, so it has no outcomes.
                row.waits = true;
                if (row.result.object && !row.acceptObjectAnswer)
                    warn(method, "the answer of '" + row.name + "' is built in the caller's VM and handed over"
                            + " as a global reference, which is the most expensive crossing there is;"
                            + " annotate it with @AcceptObjectAnswer if that is intended");
            }
            return;
        }

        if (!row.result.isVoid) {
            error(method, "'" + row.name + "' declares outcomes and returns a value; an answer comes back"
                    + " either as a return value or as an outcome, not both");
            return;
        }
        if (row.params.isEmpty() || !"long".equals(row.params.get(0).java)
                || !"request".equals(row.paramNames.get(0))) {
            error(method, "the first parameter of '" + row.name + "' must be 'long request': it is the id this"
                    + " call answers with");
            return;
        }
        row.leadingRequest = true;

        for (String outcome : outcomes) {
            Callback callback = parseCallback(method, row, outcome);
            if (callback != null)
                row.callbacks.add(callback);
        }
        if (row.callbacks.isEmpty())
            return;

        rows.add(row);

        // An answer is delivered on the side that made the request, while the method that sends it
        // is called on the side that answered; only the request id ties the two together.
        Side answerSide = handler.owner.caller();
        for (Callback callback : row.callbacks) {
            Row answer = new Row();
            answer.name = callback.wireName(row.name);
            answer.channel = Channel.ANSWER;
            answer.ownerSide = answerSide;
            answer.callSide = handler.owner;
            answer.owner = Packages.GENERATED + "." + callClass(answerSide);
            answer.method = "deliver";
            answer.isStatic = true;
            answer.leadingRequest = true;
            answer.descriptor = "(JI" + (callback.payload == null ? "" : callback.payload.descriptor) + ")V";
            answer.params.add(longType());
            answer.paramNames.add("request");
            if (callback.payload != null) {
                answer.params.add(callback.payload);
                answer.paramNames.add("value");
            }
            answer.result = voidType();
            callback.answer = answer;
            rows.add(answer);
        }
    }

    private boolean fill(Row row, ExecutableElement method) {
        for (VariableElement parameter : method.getParameters()) {
            TypeRef type = Vocabulary.of(parameter.asType());
            if (type == null) {
                error(parameter, "the type of '" + parameter.getSimpleName() + "' is outside the bridge's"
                        + " vocabulary; allowed are: " + Vocabulary.names());
                return false;
            }
            row.params.add(type);
            row.paramNames.add(parameter.getSimpleName().toString());
        }

        TypeRef result = Vocabulary.of(method.getReturnType());
        if (result == null) {
            error(method, "the return type of '" + row.name + "' is outside the bridge's vocabulary;"
                    + " allowed are: " + Vocabulary.names());
            return false;
        }
        row.result = result;
        row.descriptor = descriptor(row.params, result);
        return true;
    }

    private Callback parseCallback(ExecutableElement method, Row handler, String outcome) {
        int open = outcome.indexOf('(');
        int close = outcome.lastIndexOf(')');
        if (open <= 0 || close != outcome.length() - 1) {
            error(method, "the outcome '" + outcome + "' must be written as name(type), for example result(String[])");
            return null;
        }
        String name = outcome.substring(0, open).trim();
        String type = outcome.substring(open + 1, close).trim();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            error(method, "the outcome name '" + name + "' is not a Java identifier");
            return null;
        }
        if (type.indexOf(',') >= 0) {
            error(method, "the outcome '" + outcome + "' carries more than one value; an outcome has no parameter"
                    + " names, so two values of the same type could not be told apart on either side");
            return null;
        }

        Callback callback = new Callback();
        callback.name = name;
        callback.request = handler;
        if (!type.isEmpty()) {
            callback.payload = Vocabulary.parse(type);
            if (callback.payload == null) {
                error(method, "the type '" + type + "' of the outcome '" + outcome + "' is outside the bridge's"
                        + " vocabulary; allowed are: " + Vocabulary.names());
                return null;
            }
            if (!"L".equals(callback.payload.code) && !"l".equals(callback.payload.code)) {
                error(method, "the outcome '" + outcome + "' must carry a String or a String[]: an answer is"
                        + " delivered as text or as a list of them");
                return null;
            }
        }
        for (Callback other : handler.callbacks) {
            if (other.name.equals(name)) {
                error(method, "the outcome '" + name + "' is declared twice on '" + handler.name + "'");
                return null;
            }
            if (other.setter().equals(callback.setter())) {
                error(method, "the outcomes '" + other.name + "' and '" + name + "' both produce '"
                        + callback.setter() + "'");
                return null;
            }
        }
        return callback;
    }

    //endregion

    //region assigning ids and deriving the limits

    private void assign() {
        if (rows.isEmpty())
            return;

        // A pumped row travels under its name, so the name is its identity; a direct row never leaves
        // this process, so overloads may share a name as long as the signatures differ.
        Set<String> names = new LinkedHashSet<>();
        Set<String> signatures = new LinkedHashSet<>();
        for (Row row : rows) {
            if (!signatures.add(row.name + row.descriptor))
                error(null, "two declarations both produce " + row.name + row.descriptor);
            if (row.channel.pumped() && !names.add(row.name))
                error(null, "the bus name '" + row.name + "' is declared more than once; a name is the wire"
                        + " identity of a row, so it has to be unique");
            if (!row.callbacks.isEmpty() && !holderNames.add(Names.holder(row.name)))
                error(null, "two requests both want the holder class " + Names.holder(row.name));
        }

        assignConstants();

        rows.sort(Comparator.comparing(row -> row.constant));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).id = i;
            rows.get(i).enumName = Names.pascal(rows.get(i).constant);
        }

        for (Row row : rows) {
            if (!row.channel.pumped())
                continue;
            int slots = 0;
            int refs = 0;
            for (TypeRef param : row.payload()) {
                if (param.object)
                    refs++;
                else
                    slots += param.isLong() || "D".equals(param.code) ? 2 : 1;
            }
            maxArgs = Math.max(maxArgs, slots);
            maxParams = Math.max(maxParams, row.params.size());
            if (row.waits)
                maxRefs = Math.max(maxRefs, refs);
            else
                maxBoxed = Math.max(maxBoxed, refs);
        }
        if (maxBoxed > MAX_BOXED)
            error(null, "an asynchronous row carries " + maxBoxed + " object parameters; a boxed payload"
                    + " counts them in one byte, so at most " + MAX_BOXED + " are allowed");
    }

    private void assignConstants() {
        Map<String, List<Row>> byBase = new LinkedHashMap<>();
        for (Row row : rows)
            byBase.computeIfAbsent(Names.constant(row.name), base -> new ArrayList<>()).add(row);

        for (Map.Entry<String, List<Row>> entry : byBase.entrySet()) {
            List<Row> group = entry.getValue();
            if (group.size() == 1) {
                group.get(0).constant = entry.getKey();
                continue;
            }
            for (Row row : group) {
                String codes = row.paramCodes();
                row.constant = entry.getKey() + "_" + (codes.isEmpty() ? "V" : codes.substring(0, 1));
            }
            Set<String> seen = new LinkedHashSet<>();
            boolean collides = false;
            for (Row row : group)
                collides |= !seen.add(row.constant);
            if (collides) {
                for (Row row : group) {
                    String codes = row.paramCodes();
                    row.constant = entry.getKey() + "_" + (codes.isEmpty() ? "V" : codes);
                }
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        for (Row row : rows) {
            if (!seen.add(row.constant))
                error(null, "two rows both want the constant " + row.constant
                        + "; give one of the declarations a different name");
        }
    }

    //endregion

    //region helpers

    private TypeElement annotation(String name) {
        return processingEnv.getElementUtils().getTypeElement(name);
    }

    private static boolean hasAnnotation(Element element, String name) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(name))
                return true;
        }
        return false;
    }

    private List<ExecutableElement> methods(Set<? extends Element> elements) {
        List<ExecutableElement> found = new ArrayList<>(ElementFilter.methodsIn(elements));
        found.sort(Comparator.comparing(method -> method.getSimpleName().toString()));
        return found;
    }

    private static TypeElement owner(ExecutableElement method) {
        return (TypeElement) method.getEnclosingElement();
    }

    private List<String> callbackStrings(ExecutableElement method, String annotation) {
        for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(annotation))
                continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("callbacks"))
                    continue;
                List<String> outcomes = new ArrayList<>();
                Object value = entry.getValue().getValue();
                if (value instanceof List<?>) {
                    for (Object item : (List<?>) value)
                        outcomes.add(String.valueOf(((AnnotationValue) item).getValue()));
                }
                return outcomes;
            }
        }
        return new ArrayList<>();
    }

    private void requireCallable(ExecutableElement method) {
        if (method.getModifiers().contains(Modifier.PRIVATE))
            error(method, "'" + method.getSimpleName() + "' is private; native calls it by name");
        if (overrides(method))
            error(method, "'" + method.getSimpleName() + "' overrides a method of a supertype, so native could call"
                    + " the wrong one; the bus name has to be a name of its own");
    }

    /** Whether a supertype declares the same method, which would let native call the wrong one. */
    private boolean overrides(ExecutableElement method) {
        TypeElement owner = owner(method);
        Elements elements = processingEnv.getElementUtils();
        for (TypeElement supertype : supertypes(owner)) {
            for (ExecutableElement other : ElementFilter.methodsIn(supertype.getEnclosedElements())) {
                if (!other.getSimpleName().contentEquals(method.getSimpleName()))
                    continue;
                if (elements.overrides(method, other, owner))
                    return true;
            }
        }
        return false;
    }

    private List<TypeElement> supertypes(TypeElement type) {
        List<TypeElement> found = new ArrayList<>();
        collectSupertypes(type, found);
        return found;
    }

    private void collectSupertypes(TypeElement type, List<TypeElement> found) {
        TypeMirror superclass = type.getSuperclass();
        if (superclass != null && superclass.getKind() == TypeKind.DECLARED) {
            TypeElement element = (TypeElement) ((DeclaredType) superclass).asElement();
            if (!"java.lang.Object".equals(element.getQualifiedName().toString()) && found.add(element))
                collectSupertypes(element, found);
        }
        for (TypeMirror iface : type.getInterfaces()) {
            TypeElement element = (TypeElement) ((DeclaredType) iface).asElement();
            if (found.add(element))
                collectSupertypes(element, found);
        }
    }

    private static TypeRef longType() {
        return Vocabulary.parse("long");
    }

    private static TypeRef voidType() {
        return Vocabulary.parse("void");
    }

    static String descriptor(List<TypeRef> params, TypeRef result) {
        StringBuilder text = new StringBuilder("(");
        for (TypeRef param : params)
            text.append(param.descriptor);
        return text.append(')').append(result.descriptor).toString();
    }

    void error(Element element, String message) {
        Output.error(processingEnv, element, message);
    }

    void warn(Element element, String message) {
        Output.warn(processingEnv, element, message);
    }

    //endregion

    //region output

    void writeJava(String packageName, String simpleName, String content) throws IOException {
        Output.writeJava(processingEnv, packageName, simpleName, content);
    }

    void writeCpp(String name, String content) throws IOException {
        Output.writeCpp(processingEnv, name, content);
    }
    //endregion
}
