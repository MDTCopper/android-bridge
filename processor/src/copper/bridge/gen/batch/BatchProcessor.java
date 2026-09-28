package copper.bridge.gen.batch;

import copper.bridge.gen.Output;
import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
import copper.bridge.gen.TypeRef;
import copper.bridge.gen.Vocabulary;
import copper.bridge.gen.WireType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Reads the bridge's stream declarations and writes both sides of every batch channel from them. A channel is one
 * annotated abstract class: its fields are the header of a frame, each abstract method is one kind of record, and
 * the annotation carries both bounds a batch needs because it has no wake-up of its own. The declarations are the
 * only source, so the writer's methods and the reader's switch cannot disagree.
 */
@SupportedAnnotationTypes({BatchProcessor.ART_BATCH, BatchProcessor.JVM_BATCH})
@SupportedOptions({Output.OUTPUT_OPTION})
public final class BatchProcessor extends AbstractProcessor {
    static final String ART_BATCH = Packages.ANNOTATIONS + ".ArtBatchHandler";
    static final String JVM_BATCH = Packages.ANNOTATIONS + ".JvmBatchHandler";
    static final String SENDER_SIGNATURE = Packages.ANNOTATIONS + ".SenderSignature";

    private final List<Schema> schemas = new ArrayList<>();
    private boolean generated;

    List<Schema> schemas() {
        return schemas;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (generated || round.processingOver())
            return false;

        TypeElement jvmBatch = annotation(JVM_BATCH);
        TypeElement artBatch = annotation(ART_BATCH);
        if (jvmBatch == null && artBatch == null)
            return false;

        List<TypeElement> declared = new ArrayList<>();
        if (jvmBatch != null)
            declared.addAll(ElementFilter.typesIn(round.getElementsAnnotatedWith(jvmBatch)));
        if (artBatch != null)
            declared.addAll(ElementFilter.typesIn(round.getElementsAnnotatedWith(artBatch)));
        if (declared.isEmpty())
            return false;

        generated = true;
        try {
            // The annotation names the side that writes the records; the other side receives them.
            if (jvmBatch != null) {
                for (TypeElement type : ElementFilter.typesIn(round.getElementsAnnotatedWith(jvmBatch)))
                    collectSchema(type, Side.JVM.caller(), jvmBatch);
            }
            if (artBatch != null) {
                for (TypeElement type : ElementFilter.typesIn(round.getElementsAnnotatedWith(artBatch)))
                    collectSchema(type, Side.ART.caller(), artBatch);
            }
            BatchGen.generate(this);
        } catch (IOException e) {
            error(null, "cannot write the generated batch channels: " + e);
        }
        return true;
    }

    //region collecting

    private void collectSchema(TypeElement type, Side receiver, TypeElement annotation) {
        Schema schema = new Schema();
        schema.owner = type.getQualifiedName().toString();
        schema.receiver = receiver;

        // A channel has no wake-up of its own, so its receiver must be the side that already turns every frame.
        if (receiver != Side.JVM) {
            error(type, "a batch channel can only be received on the side that is already turning every"
                    + " frame, which is the JVM side; the annotation names the side that writes the"
                    + " records, so a channel received on ART has nothing to drain it");
            return;
        }

        final String name = type.getSimpleName().toString();
        if (!name.endsWith("Batch") || name.length() == "Batch".length()) {
            error(type, "a batch schema is named <something>Batch: the accessor is the name without the suffix,"
                    + " and " + name + " leaves none");
            return;
        }
        schema.accessor = Character.toLowerCase(name.charAt(0))
                + name.substring(1, name.length() - "Batch".length());

        for (Schema other : schemas) {
            if (other.accessor.equals(schema.accessor))
                error(type, "two schemas both want the accessor name '" + schema.accessor + "'");
        }

        schema.withLock = booleanValue(type, annotation, "withLock", false);
        schema.maxBatches = intValue(type, annotation, "maxBatches", 0);
        schema.maxBytes = intValue(type, annotation, "maxBytes", 0);
        if (schema.maxBatches <= 0 || schema.maxBytes <= 0) {
            error(type, "a batch channel needs both bounds: maxBatches and maxBytes say how much backlog may"
                    + " exist before the sender starts dropping frames");
            return;
        }

        for (Element member : type.getEnclosedElements()) {
            if (member.getKind() == ElementKind.FIELD)
                collectHeader(type, schema, (VariableElement) member);
            else if (member.getKind() == ElementKind.METHOD)
                collectRecord(type, schema, (ExecutableElement) member);
        }

        if (schema.records.isEmpty())
            error(type, "a batch schema needs at least one record: one abstract method per kind of event");
        if (schema.records.size() > 255)
            error(type, "a batch schema may hold at most 255 record kinds; " + schema.records.size()
                    + " were declared");

        // Ids follow declaration order so the two generators of one run cannot drift.
        schema.id = schemas.size();
        schemas.add(schema);
    }

    private void collectHeader(TypeElement type, Schema schema, VariableElement field) {
        if (field.getModifiers().contains(Modifier.STATIC))
            return;
        if (field.getModifiers().contains(Modifier.FINAL)) {
            error(field, "a header field cannot be final: its buffer is allocated when an instance is bound");
            return;
        }
        if (field.getModifiers().contains(Modifier.PRIVATE)) {
            error(field, "a header field cannot be private: the generated reader writes it");
            return;
        }

        Field header = new Field();
        header.name = field.getSimpleName().toString();
        if (!fillField(type, header, field.asType(), field)) {
            return;
        }

        List<String> forms = senderForms(field);
        if (header.isSequence()) {
            // A header field is written directly, so it has exactly one form and no overloads.
            if (forms.size() != 1) {
                error(field, "a header sequence takes exactly one @SenderSignature form; "
                        + forms.size() + " were listed");
                return;
            }
        } else if (!forms.isEmpty()) {
            error(field, "a header scalar needs no @SenderSignature: the generated field is the form");
            return;
        }
        header.forms.addAll(forms);

        for (Field other : schema.headers) {
            if (other.name.equals(header.name)) {
                error(field, "the header field '" + header.name + "' is declared twice");
                return;
            }
        }
        schema.headers.add(header);
    }

    private void collectRecord(TypeElement type, Schema schema, ExecutableElement method) {
        if (!method.getModifiers().contains(Modifier.ABSTRACT))
            return;

        Record record = new Record();
        record.name = method.getSimpleName().toString();
        record.id = schema.records.size();

        for (VariableElement parameter : method.getParameters()) {
            Field param = new Field();
            param.name = parameter.getSimpleName().toString();
            if (!fillField(type, param, parameter.asType(), parameter))
                return;
            record.params.add(param);
        }

        // A record of scalars needs no @SenderSignature: the wire form is the signature. A sequence does - an
        // array and a wire buffer are the same run of elements but not the same type - so it lists its callers' forms.
        List<List<String>> signatures = senderSignatures(method);
        if (signatures.isEmpty() && !record.scalarsOnly()) {
            error(method, "'" + record.name + "' has a sequence parameter but no @SenderSignature: a caller"
                    + " needs a source form to write it from");
            return;
        }
        for (List<String> signature : signatures) {
            if (signature.size() != record.params.size()) {
                error(method, "a @SenderSignature of '" + record.name + "' lists " + signature.size()
                        + " parameters, but the record takes " + record.params.size());
                return;
            }
            for (int i = 0; i < signature.size(); i++) {
                Field param = record.params.get(i);
                final String form = signature.get(i);
                if (!wireCompatible(param, form)) {
                    error(method, "the form '" + form + "' of '" + param.name + "' is not the wire shape of "
                            + param.java() + "; a source form must describe the same sequence");
                    return;
                }
            }
            record.signatures.add(signature);
        }

        for (Field header : schema.headers) {
            if (header.name.equals(record.name))
                error(method, "the record '" + record.name + "' collides with a header field of the same name");
        }
        for (Record other : schema.records) {
            if (other.name.equals(record.name))
                error(method, "the record '" + record.name + "' is declared twice");
        }
        schema.records.add(record);
    }

    private boolean fillField(TypeElement type, Field field, TypeMirror mirror, Element where) {
        WireType sequence = WireType.of(mirror.toString());
        if (sequence != null) {
            field.sequence = sequence;
            return true;
        }

        TypeRef scalar = Vocabulary.of(mirror);
        if (scalar == null || scalar.isVoid) {
            error(where, "the type " + mirror + " is neither a scalar the bridge carries nor one of the wire"
                    + " buffers (" + WireType.names() + ")");
            return false;
        }
        field.scalar = scalar;
        return true;
    }

    /**
     * Whether one listed source form describes the same wire shape as the parameter it fills: an {@code int[]} and
     * a {@code WireIntBuffer} are the same run of 32 bit integers, a {@code long[]} is not.
     */
    private boolean wireCompatible(Field param, String form) {
        if (!param.isSequence())
            return param.scalar.java.equals(simpleName(form));
        return param.sequence.code.equals(sourceCode(form));
    }

    private static String sourceCode(String form) {
        switch (simpleName(form)) {
            case "byte[]":
            case "ByteBuffer": return "b";
            case "char[]":
            case "CharBuffer":
            case "String": return "c";
            case "short[]":
            case "ShortBuffer": return "s";
            case "int[]":
            case "IntBuffer": return "i";
            case "long[]":
            case "LongBuffer": return "j";
            case "float[]":
            case "FloatBuffer": return "f";
            case "double[]":
            case "DoubleBuffer": return "d";
            case "String[]": return "l";
            default: return "?";
        }
    }

    private static String simpleName(String type) {
        final int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    private List<String> senderForms(Element element) {
        List<List<String>> all = senderSignatures(element);
        return all.isEmpty() ? new ArrayList<>() : all.get(0);
    }

    private List<List<String>> senderSignatures(Element element) {
        List<List<String>> found = new ArrayList<>();
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(SENDER_SIGNATURE))
                continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("value"))
                    continue;
                List<String> forms = new ArrayList<>();
                Object value = entry.getValue().getValue();
                if (value instanceof List<?>) {
                    for (Object item : (List<?>) value)
                        forms.add(String.valueOf(((AnnotationValue) item).getValue()));
                }
                found.add(forms);
            }
        }
        return found;
    }

    private int intValue(TypeElement type, TypeElement annotation, String name, int fallback) {
        return ((Number) annotationValue(type, annotation, name, fallback)).intValue();
    }

    private boolean booleanValue(TypeElement type, TypeElement annotation, String name, boolean fallback) {
        return (Boolean) annotationValue(type, annotation, name, fallback);
    }

    private Object annotationValue(TypeElement type, TypeElement annotation, String name, Object fallback) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(annotation.getQualifiedName().toString()))
                continue;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals(name))
                    return entry.getValue().getValue();
            }
        }
        return fallback;
    }
    //endregion

    //region helpers

    private TypeElement annotation(String name) {
        return processingEnv.getElementUtils().getTypeElement(name);
    }

    void error(Element element, String message) {
        Output.error(processingEnv, element, message);
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
