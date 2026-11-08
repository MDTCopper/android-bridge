package copper.bridge.compat.api;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;

import javax.lang.model.element.Modifier;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The declarations of one source file, through the JDK parser.
 *
 * <p>Parsing only: nothing is attributed or resolved, so a source file can be read without its dependencies. The
 * signature text is built from the tree instead of the file text, which drops parameter names, annotations and
 * formatting and leaves what the JVM actually links against.
 */
public final class JavaApi {

    private JavaApi() {
    }

    public static final class Type {
        public final ClassTree tree;
        public final String kind;

        Type(ClassTree tree) {
            this.tree = tree;
            this.kind = tree.getKind().name();
        }
    }

    /** Finds {@code outerName} (and {@code nestedName} inside it) in one source file; null when absent. */
    public static Type type(String source, String fileName, String outerName, String nestedName) {
        CompilationUnitTree unit = parse(source, fileName);
        if (unit == null)
            return null;

        ClassTree outer = null;
        for (Tree declaration : unit.getTypeDecls()) {
            if (declaration instanceof ClassTree
                    && ((ClassTree) declaration).getSimpleName().contentEquals(outerName)) {
                outer = (ClassTree) declaration;
                break;
            }
        }
        if (outer == null)
            return null;
        if (nestedName == null)
            return new Type(outer);

        ClassTree nested = findNested(outer, nestedName);
        return nested == null ? null : new Type(nested);
    }

    /** Every declaration of {@code member} in the type, with parameter names and annotations removed. */
    public static Set<MemberSignature> members(ClassTree type, String member) {
        Set<MemberSignature> found = new LinkedHashSet<>();
        for (Tree declaration : type.getMembers()) {
            if (declaration instanceof MethodTree) {
                MethodTree method = (MethodTree) declaration;
                String name = method.getReturnType() == null ? "<init>" : method.getName().toString();
                if (name.equals(member))
                    found.add(new MemberSignature(methodSignature(method), method.getParameters().size()));
            } else if (declaration instanceof VariableTree) {
                VariableTree field = (VariableTree) declaration;
                if (field.getName().contentEquals(member))
                    found.add(new MemberSignature(fieldSignature(field), -1));
            }
        }
        return found;
    }

    private static ClassTree findNested(ClassTree type, String name) {
        for (Tree member : type.getMembers()) {
            if (member instanceof ClassTree) {
                ClassTree nested = (ClassTree) member;
                if (nested.getSimpleName().contentEquals(name))
                    return nested;
                ClassTree deeper = findNested(nested, name);
                if (deeper != null)
                    return deeper;
            }
        }
        return null;
    }

    private static String methodSignature(MethodTree method) {
        String modifiers = modifiers(method.getModifiers());
        String typeParameters = method.getTypeParameters().isEmpty()
                ? ""
                : "<" + method.getTypeParameters().stream().map(TypeParameterTree::toString)
                        .collect(Collectors.joining(", ")) + ">";
        String returnType = method.getReturnType() == null ? "" : typeText(method.getReturnType());
        String name = method.getReturnType() == null ? "<init>" : method.getName().toString();
        String parameters = method.getParameters().stream().map(parameter -> typeText(parameter.getType()))
                .collect(Collectors.joining(", "));
        return normalize(join(modifiers, typeParameters, returnType) + " " + name + "(" + parameters + ")");
    }

    private static String fieldSignature(VariableTree field) {
        String type = field.getType() == null ? "" : typeText(field.getType());
        return normalize(join(modifiers(field.getModifiers()), type) + " " + field.getName());
    }

    private static String modifiers(ModifiersTree tree) {
        List<Modifier> flags = new ArrayList<>(tree.getFlags());
        flags.sort(Comparator.comparing(Enum::name));
        return flags.stream().map(Modifier::name).collect(Collectors.joining(" "));
    }

    private static String typeText(Tree type) {
        if (type == null)
            return "";
        return normalize(type.toString().replaceAll("@[\\w$.]+(?:\\([^)]*\\))?\\s*", ""));
    }

    private static String join(String... parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty())
                continue;
            if (text.length() > 0)
                text.append(' ');
            text.append(part.trim());
        }
        return text.toString();
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static CompilationUnitTree parse(String source, String fileName) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new IllegalStateException("no system Java compiler; run the check on a JDK");
        DiagnosticListener<JavaFileObject> quiet = diagnostic -> {
        };
        try (StandardJavaFileManager manager =
                     compiler.getStandardFileManager(quiet, null, StandardCharsets.UTF_8)) {
            JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + fileName),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            JavacTask task = (JavacTask) compiler.getTask(null, manager, quiet,
                    List.of("-proc:none"), null, List.of(file));
            for (CompilationUnitTree unit : task.parse())
                return unit;
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse " + fileName, e);
        }
        return null;
    }
}
