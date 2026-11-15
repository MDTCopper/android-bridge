package copper.bridge.compat.refs;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Reads the methods a branch class declares and whether it marked them {@code @BridgeOverride}. */
public final class BridgeOverrideRefs {
    private static final String ANNOTATION = "Lcopper/bridge/annotation/BridgeOverride;";
    private static final String RANGE = "Lcopper/bridge/annotation/CompatRange;";

    private BridgeOverrideRefs() {
    }

    public static List<BridgeOverrideRef> read(Path directory) {
        List<BridgeOverrideRef> refs = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String className = directory.relativize(path).toString().replace('\\', '/');
                className = className.substring(0, className.length() - ".class".length());
                try {
                    new ClassReader(Files.readAllBytes(path)).accept(new Collector(className, refs),
                            ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (Throwable ignored) {
                    // an unreadable class is not an annotation source; the compiler already vetted it
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return refs;
    }

    private static final class Collector extends ClassVisitor {
        private final String className;
        private final List<BridgeOverrideRef> refs;
        private String overrideKey;

        Collector(String className, List<BridgeOverrideRef> refs) {
            super(Opcodes.ASM9);
            this.className = className;
            this.refs = refs;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (!RANGE.equals(descriptor))
                return null;
            return new AnnotationVisitor(Opcodes.ASM9) {
                private String floor = "default";
                private String ceiling = "default";

                @Override
                public void visit(String name, Object value) {
                    if ("floor".equals(name))
                        floor = (String) value;
                    else if ("ceiling".equals(name))
                        ceiling = (String) value;
                }

                @Override
                public void visitEnd() {
                    overrideKey = floor + "|" + ceiling;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            if (name.startsWith("<") || (access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE
                    | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0)
                return null;

            BridgeOverrideRef ref = new BridgeOverrideRef(className, className, name, descriptor, overrideKey);
            refs.add(ref);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (ANNOTATION.equals(annotation))
                        ref.annotated = true;
                    return null;
                }
            };
        }
    }
}
