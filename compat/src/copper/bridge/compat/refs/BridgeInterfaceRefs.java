package copper.bridge.compat.refs;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Reads the methods a branch marked {@code @BridgeInterface}. */
public final class BridgeInterfaceRefs {
    private static final String ANNOTATION = "Lcopper/bridge/annotation/BridgeInterface;";

    private BridgeInterfaceRefs() {
    }

    public static List<BridgeInterfaceRef> read(Path directory) {
        List<BridgeInterfaceRef> refs = new ArrayList<>();
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
        private final List<BridgeInterfaceRef> refs;

        Collector(String className, List<BridgeInterfaceRef> refs) {
            super(Opcodes.ASM9);
            this.className = className;
            this.refs = refs;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (ANNOTATION.equals(annotation))
                        refs.add(new BridgeInterfaceRef(className, className, name, descriptor));
                    return null;
                }
            };
        }
    }
}
