package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reads the class and member names the branch reaches through reflection.
 *
 * <p>Only {@code Class.forName} and the {@code Class} lookup calls are followed; a class token passed around
 * without a member lookup is not a target. The names have to be literals or locals the analyzer can trace back
 * to one, which is how the branch writes them.
 */
public final class ReflectionRefs {
    private static final String COMPAT_RANGE = "Lcopper/bridge/annotation/CompatRange;";

    private ReflectionRefs() {
    }

    public static List<ReflectionRef> read(Path directory) {
        List<ReflectionRef> refs = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String className = directory.relativize(path).toString().replace('\\', '/');
                className = className.substring(0, className.length() - ".class".length());
                readClass(path, className, refs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, ReflectionRef> unique = new LinkedHashMap<>();
        for (ReflectionRef ref : refs)
            unique.putIfAbsent(ref.key(), ref);
        return new ArrayList<>(unique.values());
    }

    private static void readClass(Path path, String sourceClass, List<ReflectionRef> refs) {
        readInto(path, sourceClass, refs);
    }

    /** Every reflection target in one class file. */
    public static List<ReflectionRef> readClass(Path path, String sourceClass) {
        List<ReflectionRef> refs = new ArrayList<>();
        readInto(path, sourceClass, refs);
        return refs;
    }

    private static void readInto(Path path, String sourceClass, List<ReflectionRef> refs) {
        try (InputStream in = Files.newInputStream(path)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            String override = overrideKey(node);
            for (MethodNode method : node.methods) {
                Interpreter interpreter = new Interpreter(sourceClass, override, refs);
                try {
                    new Analyzer<>(interpreter).analyze(node.name, method);
                } catch (AnalyzerException | RuntimeException ignored) {
                    // a method the analyzer cannot follow contributes no reflection target
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String overrideKey(ClassNode node) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.invisibleAnnotations != null)
            annotations.addAll(node.invisibleAnnotations);
        if (node.visibleAnnotations != null)
            annotations.addAll(node.visibleAnnotations);
        for (AnnotationNode annotation : annotations) {
            if (!COMPAT_RANGE.equals(annotation.desc))
                continue;
            String floor = null, ceiling = null;
            if (annotation.values != null) {
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    String key = (String)annotation.values.get(i);
                    Object value = annotation.values.get(i + 1);
                    if ("floor".equals(key))
                        floor = (String)value;
                    else if ("ceiling".equals(key))
                        ceiling = (String)value;
                }
            }
            return (floor == null ? "default" : floor) + "|" + (ceiling == null ? "default" : ceiling);
        }
        return null;
    }

    private static final class Interpreter extends BasicInterpreter {
        private final String sourceClass;
        private final String override;
        private final List<ReflectionRef> refs;

        Interpreter(String sourceClass, String override, List<ReflectionRef> refs) {
            super(Opcodes.ASM9);
            this.sourceClass = sourceClass;
            this.override = override;
            this.refs = refs;
        }

        @Override
        public BasicValue newValue(Type type) {
            if (type != null && type.getSort() == Type.OBJECT)
                return new Sym(Kind.OTHER, type.getInternalName());
            return super.newValue(type);
        }

        @Override
        public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
            if (insn.getOpcode() == Opcodes.LDC) {
                Object value = ((LdcInsnNode)insn).cst;
                if (value instanceof String)
                    return new Sym(Kind.STRING, (String)value);
                if (value instanceof Type && ((Type)value).getSort() == Type.OBJECT)
                    return new Sym(Kind.CLASS, ((Type)value).getInternalName());
            }
            return super.newOperation(insn);
        }

        @Override
        public BasicValue copyOperation(AbstractInsnNode insn, BasicValue value) {
            return value;
        }

        @Override
        public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
            // CHECKCAST keeps the symbolic value, and that is the only unary operation reflection code uses
            if (insn.getOpcode() == Opcodes.CHECKCAST)
                return value;
            return super.unaryOperation(insn, value);
        }

        @Override
        public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values)
                throws AnalyzerException {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode method = (MethodInsnNode)insn;
                if ("java/lang/Class".equals(method.owner)) {
                    if ("forName".equals(method.name) && values.size() == 1) {
                        String name = string(values.get(0));
                        if (name != null)
                            return new Sym(Kind.CLASS, name.replace('.', '/'));
                    }
                    if ("getMethod".equals(method.name) || "getDeclaredMethod".equals(method.name)) {
                        add(values, ReflectionRef.Kind.METHOD);
                        return new Sym(Kind.OTHER, "java/lang/reflect/Method");
                    }
                    if ("getField".equals(method.name) || "getDeclaredField".equals(method.name)) {
                        add(values, ReflectionRef.Kind.FIELD);
                        return new Sym(Kind.OTHER, "java/lang/reflect/Field");
                    }
                    if ("getConstructor".equals(method.name) || "getDeclaredConstructor".equals(method.name)) {
                        String owner = classValue(values.size() > 0 ? values.get(0) : null);
                        if (owner != null)
                            refs.add(new ReflectionRef(sourceClass, owner, "<init>",
                                    ReflectionRef.Kind.CONSTRUCTOR, override));
                        return new Sym(Kind.OTHER, "java/lang/reflect/Constructor");
                    }
                }
                // the branch's setStatic(Class, String, Object) helper is a field lookup with the class and name
                // as ordinary arguments, so the analyzer cannot see the getDeclaredField inside it
                if ("setStatic".equals(method.name) && values.size() >= 2
                        && method.owner.startsWith("copper/bridge/")) {
                    String owner = classValue(values.get(0));
                    String member = string(values.get(1));
                    if (owner != null && member != null)
                        refs.add(new ReflectionRef(sourceClass, owner, member, ReflectionRef.Kind.FIELD, override));
                }
            }
            return super.naryOperation(insn, values);
        }

        private void add(List<? extends BasicValue> values, ReflectionRef.Kind kind) {
            if (values.size() < 2)
                return;
            String owner = classValue(values.get(0));
            String member = string(values.get(1));
            if (owner != null && member != null)
                refs.add(new ReflectionRef(sourceClass, owner, member, kind, override));
        }

        private static String classValue(BasicValue value) {
            return value instanceof Sym && ((Sym)value).kind == Kind.CLASS ? ((Sym)value).text : null;
        }

        private static String string(BasicValue value) {
            return value instanceof Sym && ((Sym)value).kind == Kind.STRING ? ((Sym)value).text : null;
        }
    }

    private enum Kind {
        CLASS, STRING, OTHER
    }

    private static final class Sym extends BasicValue {
        final Kind kind;
        final String text;

        Sym(Kind kind, String text) {
            super(Type.getObjectType("java/lang/Object"));
            this.kind = kind;
            this.text = text;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Sym))
                return false;
            Sym sym = (Sym)other;
            return kind == sym.kind && Objects.equals(text, sym.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, text);
        }
    }
}
