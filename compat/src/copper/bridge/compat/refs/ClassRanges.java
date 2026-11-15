package copper.bridge.compat.refs;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** The {@code @CompatRange} each branch class declares, by internal class name. */
public final class ClassRanges {
    private static final String RANGE = "Lcopper/bridge/annotation/CompatRange;";

    private ClassRanges() {
    }

    public static Map<String, String> read(Path directory) {
        Map<String, String> ranges = new HashMap<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String name = directory.relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                try {
                    String[] found = new String[1];
                    new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!RANGE.equals(descriptor))
                                return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                private String floor = "default";
                                private String ceiling = "default";

                                @Override
                                public void visit(String key, Object value) {
                                    if ("floor".equals(key))
                                        floor = (String) value;
                                    else if ("ceiling".equals(key))
                                        ceiling = (String) value;
                                }

                                @Override
                                public void visitEnd() {
                                    found[0] = floor + "|" + ceiling;
                                }
                            };
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (found[0] != null)
                        ranges.put(name, found[0]);
                } catch (Throwable ignored) {
                    // an unreadable class was already reported by the compiler
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ranges;
    }
}
