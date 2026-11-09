package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Reads the class/field names a branch passes to arc's {@code Reflect.set} and {@code Reflect.get}. */
public final class ArcReflectRefs {
    private ArcReflectRefs() {
    }

    public static List<ArcReflectRef> read(Path directory) {
        List<ArcReflectRef> refs = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String className = directory.relativize(path).toString().replace('\\', '/');
                className = className.substring(0, className.length() - ".class".length());
                readInto(path, className, refs);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, ArcReflectRef> unique = new LinkedHashMap<>();
        for (ArcReflectRef ref : refs)
            unique.putIfAbsent(ref.key(), ref);
        return new ArrayList<>(unique.values());
    }

    private static void readInto(Path path, String sourceClass, List<ArcReflectRef> refs) {
        try (InputStream in = Files.newInputStream(path)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            for (MethodNode method : node.methods) {
                try {
                    new Analyzer<>(new Interpreter(sourceClass, refs)).analyze(node.name, method);
                } catch (AnalyzerException | RuntimeException ignored) {
                    // a method the analyzer cannot follow contributes no Reflect target
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class Interpreter extends BasicInterpreter {
        private final String sourceClass;
        private final List<ArcReflectRef> refs;

        Interpreter(String sourceClass, List<ArcReflectRef> refs) {
            super(Opcodes.ASM9);
            this.sourceClass = sourceClass;
            this.refs = refs;
        }

        @Override
        public BasicValue newValue(Type type) {
            if (type != null && type.getSort() == Type.OBJECT)
                return new Sym(Kind.OTHER, null);
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
            if (insn.getOpcode() == Opcodes.CHECKCAST)
                return value;
            return super.unaryOperation(insn, value);
        }

        @Override
        public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode method = (MethodInsnNode)insn;
                if (isReflect(method.owner)) {
                    String owner = classValue(values.isEmpty() ? null : values.get(0));
                    String member = null;
                    if ("set".equals(method.name) && values.size() == 3)
                        member = string(values.get(1));
                    else if ("set".equals(method.name) && values.size() == 4)
                        member = string(values.get(2));
                    else if ("get".equals(method.name) && values.size() == 2)
                        member = string(values.get(1));
                    if (owner != null && member != null)
                        refs.add(new ArcReflectRef(sourceClass, owner, member));
                }
            }
            return super.naryOperation(insn, values);
        }

        private static boolean isReflect(String owner) {
            return "arc/util/Reflect".equals(owner) || "io/anuke/arc/util/Reflect".equals(owner);
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
