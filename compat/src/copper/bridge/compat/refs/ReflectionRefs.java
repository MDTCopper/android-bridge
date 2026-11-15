package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reads the class and member names the branch reaches through reflection. A target the analyzer cannot trace is an
 * issue, not a silent skip. An annotated {@code @BridgeReflect} helper is resolved at its call sites.
 */
public final class ReflectionRefs {
    private static final String COMPAT_RANGE = "Lcopper/bridge/annotation/CompatRange;";
    private static final String BRIDGE_REFLECT = "Lcopper/bridge/annotation/BridgeReflect;";
    private static final Set<String> WHOLE_CLASS = new HashSet<>(Arrays.asList(
            "getMethods", "getDeclaredMethods", "getFields", "getDeclaredFields",
            "getConstructors", "getDeclaredConstructors", "getClasses", "getDeclaredClasses"));

    private ReflectionRefs() {
    }

    /** A bridge helper annotated with {@code @BridgeReflect}, resolved at its call sites. */
    private static final class Helper {
        final ReflectionRef.Kind kind;
        final int classArg;
        final String owner;
        final int memberArg;
        final String member;

        Helper(ReflectionRef.Kind kind, int classArg, String owner, int memberArg, String member) {
            this.kind = kind;
            this.classArg = classArg;
            this.owner = owner;
            this.memberArg = memberArg;
            this.member = member;
        }
    }

    /** The targets read from a directory, and the reflection calls that could not be resolved. */
    public static final class Result {
        public final List<ReflectionRef> refs = new ArrayList<>();
        public final List<String> issues = new ArrayList<>();
    }

    public static Result read(Path directory) {
        Result result = new Result();
        Map<String, Helper> helpers = new HashMap<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                files.add(path);
                helpers.putAll(helpers(path, result));
            }
            for (Path path : files) {
                String className = directory.relativize(path).toString().replace('\\', '/');
                className = className.substring(0, className.length() - ".class".length());
                readInto(path, className, result, helpers);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, ReflectionRef> unique = new LinkedHashMap<>();
        for (ReflectionRef ref : result.refs)
            unique.putIfAbsent(ref.key(), ref);
        result.refs.clear();
        result.refs.addAll(unique.values());
        return result;
    }

    /** Every reflection target in one class file. */
    public static Result readClass(Path path, String sourceClass) {
        Result result = new Result();
        Map<String, Helper> helpers = helpers(path, result);
        readInto(path, sourceClass, result, helpers);
        return result;
    }

    private static void readInto(Path path, String sourceClass, Result result, Map<String, Helper> helpers) {
        try (InputStream in = Files.newInputStream(path)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            String override = overrideKey(node);
            Map<String, String> constants = constants(node);
            for (MethodNode method : node.methods) {
                if (helpers.containsKey(node.name + "." + method.name + method.desc))
                    continue;
                if (!hasReflection(method, helpers))
                    continue;
                Interpreter interpreter = new Interpreter(sourceClass, method.name, override, result, constants,
                        helpers);
                try {
                    new Analyzer<>(interpreter).analyze(node.name, method);
                } catch (AnalyzerException | RuntimeException e) {
                    result.issues.add("reflection could not be analyzed in " + sourceClass + "." + method.name);
                }
            }
        } catch (Throwable e) {
            result.issues.add("reflection could not be read in " + sourceClass);
        }
    }

    /** The {@code @BridgeReflect} helpers declared by one class, keyed by owner.method+descriptor. */
    private static Map<String, Helper> helpers(Path path, Result result) {
        Map<String, Helper> helpers = new HashMap<>();
        try (InputStream in = Files.newInputStream(path)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            for (MethodNode method : node.methods) {
                Helper helper = helper(method);
                if (helper != null)
                    helpers.put(node.name + "." + method.name + method.desc, helper);
            }
        } catch (Throwable e) {
            result.issues.add("reflection helpers could not be read in " + path);
        }
        return helpers;
    }

    private static Helper helper(MethodNode method) {
        for (AnnotationNode annotation : annotations(method)) {
            if (!BRIDGE_REFLECT.equals(annotation.desc))
                continue;
            ReflectionRef.Kind kind = null;
            int classArg = 0, memberArg = 1;
            String member = "", owner = "";
            if (annotation.values != null) {
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    String key = (String)annotation.values.get(i);
                    Object value = annotation.values.get(i + 1);
                    if ("kind".equals(key))
                        kind = ReflectionRef.Kind.valueOf(((String[])value)[1]);
                    else if ("classArg".equals(key))
                        classArg = (Integer)value;
                    else if ("owner".equals(key))
                        owner = ((Type)value).getSort() == Type.VOID ? "" : ((Type)value).getInternalName();
                    else if ("memberArg".equals(key))
                        memberArg = (Integer)value;
                    else if ("member".equals(key))
                        member = (String)value;
                }
            }
            return kind == null ? null : new Helper(kind, classArg, owner, memberArg, member);
        }
        return null;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.invisibleAnnotations != null)
            annotations.addAll(method.invisibleAnnotations);
        if (method.visibleAnnotations != null)
            annotations.addAll(method.visibleAnnotations);
        return annotations;
    }

    /** The {@code static final String} fields of one class, which class-name arguments are often built from. */
    private static Map<String, String> constants(ClassNode node) {
        Map<String, String> constants = new HashMap<>();
        for (FieldNode field : node.fields) {
            if (field.value instanceof String)
                constants.put(node.name + "." + field.name, (String)field.value);
        }
        return constants;
    }

    /** Whether the method calls anything reflection-shaped; unrelated methods are not given to the analyzer. */
    private static boolean hasReflection(MethodNode method, Map<String, Helper> helpers) {
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof MethodInsnNode))
                continue;
            MethodInsnNode call = (MethodInsnNode)insn;
            if ("java/lang/Class".equals(call.owner) || "java/lang/ClassLoader".equals(call.owner)
                    || "java/lang/invoke/MethodHandles$Lookup".equals(call.owner)
                    || GameReflectHelpers.knownOwner(call.owner)
                    || helpers.containsKey(call.owner + "." + call.name + call.desc))
                return true;
        }
        return false;
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
        private final String method;
        private final String override;
        private final Result result;
        private final Map<String, String> constants;
        private final Map<String, Helper> helpers;

        Interpreter(String sourceClass, String method, String override, Result result,
                    Map<String, String> constants, Map<String, Helper> helpers) {
            super(Opcodes.ASM9);
            this.sourceClass = sourceClass;
            this.method = method;
            this.override = override;
            this.result = result;
            this.constants = constants;
            this.helpers = helpers;
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
            if (insn.getOpcode() == Opcodes.GETSTATIC) {
                FieldInsnNode field = (FieldInsnNode)insn;
                String constant = constants.get(field.owner + "." + field.name);
                if (constant != null)
                    return new Sym(Kind.STRING, constant);
            }
            // CHECKCAST keeps the symbolic value, and that is the only unary operation reflection code uses
            if (insn.getOpcode() == Opcodes.CHECKCAST)
                return value;
            return super.unaryOperation(insn, value);
        }

        @Override
        public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values)
                throws AnalyzerException {
            if (!(insn instanceof MethodInsnNode))
                return super.naryOperation(insn, values);

            MethodInsnNode call = (MethodInsnNode)insn;
            BasicValue value = null;
            Helper bridge = helpers.get(call.owner + "." + call.name + call.desc);
            if (bridge != null) {
                helperCall(call, values, bridge.kind, bridge.classArg, bridge.owner, bridge.memberArg,
                        bridge.member);
            } else if (GameReflectHelpers.knownOwner(call.owner)) {
                GameReflectHelpers.Helper game = GameReflectHelpers.find(call.owner, call.name, call.desc);
                if (game == null) {
                    unresolved("unknown game reflection helper " + call.owner + "." + call.name);
                } else {
                    helperCall(call, values, game.kind, game.classArg, "", game.memberArg, "");
                }
            } else if ("java/lang/Class".equals(call.owner)) {
                value = classCall(call, values);
            } else if ("java/lang/ClassLoader".equals(call.owner)) {
                value = loaderCall(call, values);
            } else if ("java/lang/invoke/MethodHandles$Lookup".equals(call.owner)) {
                handleCall(call, values);
            }
            return value != null ? value : super.naryOperation(insn, values);
        }

        private BasicValue classCall(MethodInsnNode call, List<? extends BasicValue> values) {
            if ("forName".equals(call.name) && values.size() >= 1) {
                String name = string(values.get(0));
                if (name == null) {
                    unresolved("Class.forName");
                    return null;
                }
                return new Sym(Kind.CLASS, name.replace('.', '/'));
            }
            if (WHOLE_CLASS.contains(call.name)) {
                String owner = classValue(values.size() > 0 ? values.get(0) : null);
                if (owner == null) {
                    unresolved("Class." + call.name);
                    return null;
                }
                result.refs.add(new ReflectionRef(sourceClass, owner, null, ReflectionRef.Kind.TYPE, override));
                return null;
            }
            if ("getMethod".equals(call.name) || "getDeclaredMethod".equals(call.name)) {
                member(values, ReflectionRef.Kind.METHOD, "Class." + call.name);
                return new Sym(Kind.OTHER, "java/lang/reflect/Method");
            }
            if ("getField".equals(call.name) || "getDeclaredField".equals(call.name)) {
                member(values, ReflectionRef.Kind.FIELD, "Class." + call.name);
                return new Sym(Kind.OTHER, "java/lang/reflect/Field");
            }
            if ("getConstructor".equals(call.name) || "getDeclaredConstructor".equals(call.name)) {
                String owner = classValue(values.size() > 0 ? values.get(0) : null);
                if (owner == null) {
                    unresolved("Class." + call.name);
                    return null;
                }
                result.refs.add(new ReflectionRef(sourceClass, owner, "<init>", ReflectionRef.Kind.CONSTRUCTOR,
                        override));
                return new Sym(Kind.OTHER, "java/lang/reflect/Constructor");
            }
            return null;
        }

        private BasicValue loaderCall(MethodInsnNode call, List<? extends BasicValue> values) {
            if (!"loadClass".equals(call.name))
                return null;
            String name = string(values.size() > 1 ? values.get(1) : null);
            if (name == null) {
                unresolved("ClassLoader.loadClass");
                return null;
            }
            return new Sym(Kind.CLASS, name.replace('.', '/'));
        }

        private void handleCall(MethodInsnNode call, List<? extends BasicValue> values) {
            if (!call.name.startsWith("find"))
                return;
            String owner = classValue(values.size() > 1 ? values.get(1) : null);
            String name = string(values.size() > 2 ? values.get(2) : null);
            if (owner == null || name == null) {
                unresolved("MethodHandles.Lookup." + call.name);
                return;
            }
            boolean constructor = "findConstructor".equals(call.name);
            result.refs.add(new ReflectionRef(sourceClass, owner, constructor ? "<init>" : name,
                    constructor ? ReflectionRef.Kind.CONSTRUCTOR : ReflectionRef.Kind.METHOD, override));
        }

        private void helperCall(MethodInsnNode call, List<? extends BasicValue> values, ReflectionRef.Kind kind,
                                int classArg, String constantOwner, int memberArg, String constantMember) {
            String owner = constantOwner;
            if (owner == null || owner.isEmpty()) {
                if (classArg < 0 || classArg >= values.size()) {
                    unresolved(call.owner + "." + call.name + " target class is not a constant");
                    return;
                }
                owner = classValue(values.get(classArg));
            }
            if (owner == null) {
                unresolved(call.owner + "." + call.name + " target class is not a constant");
                return;
            }
            String member = constantMember;
            if (member == null || member.isEmpty()) {
                member = memberArg >= 0 && memberArg < values.size() ? string(values.get(memberArg)) : null;
            }
            if (kind != ReflectionRef.Kind.TYPE && (member == null || member.isEmpty())
                    && kind != ReflectionRef.Kind.CONSTRUCTOR) {
                unresolved(call.owner + "." + call.name + " target member is not a constant");
                return;
            }
            if (kind == ReflectionRef.Kind.CONSTRUCTOR && (member == null || member.isEmpty()))
                member = "<init>";
            result.refs.add(new ReflectionRef(sourceClass, owner, kind == ReflectionRef.Kind.TYPE ? null : member,
                    kind, override));
        }

        private void member(List<? extends BasicValue> values, ReflectionRef.Kind kind, String api) {
            if (values.size() < 2) {
                unresolved(api);
                return;
            }
            String owner = classValue(values.get(0));
            String member = string(values.get(1));
            if (owner == null || member == null) {
                unresolved(api);
                return;
            }
            result.refs.add(new ReflectionRef(sourceClass, owner, member, kind, override));
        }

        private void unresolved(String api) {
            result.issues.add("unresolved reflection in " + sourceClass + "." + method + ": " + api
                    + " target is not a literal");
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
