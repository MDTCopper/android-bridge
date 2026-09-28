package copper.bridge.gen;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Where the two generators put what they produce. The Java half goes through the filer, so javac compiles it
 * in the same run; the C++ half is written into the source tree, and the build that reads it runs after the
 * compile that writes it, so nothing is ever compiled from a stale table.
 */
public final class Output {
    public static final String OUTPUT_OPTION = "busOutput";

    private Output() {
    }

    public static void writeJava(ProcessingEnvironment env, String packageName, String simpleName,
            String content) throws IOException {
        JavaFileObject file = env.getFiler().createSourceFile(packageName + "." + simpleName);
        try (Writer writer = file.openWriter()) {
            writer.write(content);
        }
    }

    public static void writeCpp(ProcessingEnvironment env, String name, String content) throws IOException {
        Path directory = Paths.get(env.getOptions().getOrDefault(OUTPUT_OPTION, "native/src/gen"));
        Path target = directory.resolve(name);
        Files.createDirectories(target.getParent());
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void error(ProcessingEnvironment env, Element element, String message) {
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    public static void warn(ProcessingEnvironment env, Element element, String message) {
        env.getMessager().printMessage(Diagnostic.Kind.WARNING, message, element);
    }
}
