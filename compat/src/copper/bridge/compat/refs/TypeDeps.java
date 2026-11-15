package copper.bridge.compat.refs;

import org.objectweb.asm.*;
import org.objectweb.asm.signature.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Collects every class a branch depends on: supertypes, field and method descriptors, thrown exceptions, annotation
 * types, class literals, code owners, method handles. {@code ClassRefs} covers members; this covers the types behind
 * them, which the member check alone does not see.
 */
public final class TypeDeps {
    private static final String COMPAT_RANGE = "Lcopper/bridge/annotation/CompatRange;";

    private TypeDeps() {
    }

    public static List<TypeDep> read(Path directory) {
        Map<String, TypeDep> deps = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String className = directory.relativize(path).toString().replace('\\', '/');
                className = className.substring(0, className.length() - ".class".length());
                readInto(path, className, deps);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ArrayList<>(deps.values());
    }

    public static List<TypeDep> readClass(Path path, String sourceClass) {
        Map<String, TypeDep> deps = new LinkedHashMap<>();
        readInto(path, sourceClass, deps);
        return new ArrayList<>(deps.values());
    }

    private static void readInto(Path path, String sourceClass, Map<String, TypeDep> deps) {
        try (InputStream in = Files.newInputStream(path)) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG);
            String override = overrideKey(node);
            if (node.superName != null)
                add(deps, sourceClass, node.superName, override);
            for (String anInterface : node.interfaces)
                add(deps, sourceClass, anInterface, override);

            for (FieldNode field : node.fields) {
                addType(deps, sourceClass, Type.getType(field.desc), override);
                signature(deps, sourceClass, field.signature, override, false);
                annotations(deps, sourceClass, field.visibleAnnotations, override);
                annotations(deps, sourceClass, field.invisibleAnnotations, override);
                typeAnnotations(deps, sourceClass, field.visibleTypeAnnotations, override);
                typeAnnotations(deps, sourceClass, field.invisibleTypeAnnotations, override);
            }
            for (MethodNode method : node.methods) {
                addMethod(deps, sourceClass, method.desc, override);
                signature(deps, sourceClass, method.signature, override, false);
                for (String exception : method.exceptions)
                    add(deps, sourceClass, exception, override);
                annotations(deps, sourceClass, method.visibleAnnotations, override);
                annotations(deps, sourceClass, method.invisibleAnnotations, override);
                typeAnnotations(deps, sourceClass, method.visibleTypeAnnotations, override);
                typeAnnotations(deps, sourceClass, method.invisibleTypeAnnotations, override);
                parameterAnnotations(deps, sourceClass, method.visibleParameterAnnotations, override);
                parameterAnnotations(deps, sourceClass, method.invisibleParameterAnnotations, override);
                localAnnotations(deps, sourceClass, method.visibleLocalVariableAnnotations, override);
                localAnnotations(deps, sourceClass, method.invisibleLocalVariableAnnotations, override);
                for (AbstractInsnNode insn : method.instructions)
                    instruction(deps, sourceClass, insn, override);
                for (TryCatchBlockNode block : method.tryCatchBlocks) {
                    if (block.type != null)
                        add(deps, sourceClass, block.type, override);
                }
            }
            annotations(deps, sourceClass, node.visibleAnnotations, override);
            annotations(deps, sourceClass, node.invisibleAnnotations, override);
            typeAnnotations(deps, sourceClass, node.visibleTypeAnnotations, override);
            typeAnnotations(deps, sourceClass, node.invisibleTypeAnnotations, override);
            signature(deps, sourceClass, node.signature, override, true);
        } catch (Throwable ignored) {
        }
    }

    private static void instruction(Map<String, TypeDep> deps, String sourceClass, AbstractInsnNode insn,
                                    String override) {
        if (insn instanceof TypeInsnNode) {
            String desc = ((TypeInsnNode)insn).desc;
            if (desc.startsWith("["))
                addType(deps, sourceClass, Type.getType(desc), override);
            else
                add(deps, sourceClass, desc, override);
        } else if (insn instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode)insn;
            add(deps, sourceClass, field.owner, override);
            addType(deps, sourceClass, Type.getType(field.desc), override);
        } else if (insn instanceof MethodInsnNode) {
            MethodInsnNode method = (MethodInsnNode)insn;
            add(deps, sourceClass, method.owner, override);
            addMethod(deps, sourceClass, method.desc, override);
        } else if (insn instanceof LdcInsnNode) {
            Object value = ((LdcInsnNode)insn).cst;
            if (value instanceof Type)
                addType(deps, sourceClass, (Type)value, override);
            else if (value instanceof Handle)
                handle(deps, sourceClass, (Handle)value, override);
            else if (value instanceof ConstantDynamic)
                dynamic(deps, sourceClass, (ConstantDynamic)value, override);
        } else if (insn instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode)insn;
            addMethod(deps, sourceClass, dynamic.desc, override);
            handle(deps, sourceClass, dynamic.bsm, override);
            for (Object arg : dynamic.bsmArgs) {
                if (arg instanceof Type)
                    addType(deps, sourceClass, (Type)arg, override);
                else if (arg instanceof Handle)
                    handle(deps, sourceClass, (Handle)arg, override);
                else if (arg instanceof ConstantDynamic)
                    dynamic(deps, sourceClass, (ConstantDynamic)arg, override);
            }
        } else if (insn instanceof MultiANewArrayInsnNode) {
            addType(deps, sourceClass, Type.getType(((MultiANewArrayInsnNode)insn).desc), override);
        }
    }

    private static void handle(Map<String, TypeDep> deps, String sourceClass, Handle handle, String override) {
        add(deps, sourceClass, handle.getOwner(), override);
        addMethod(deps, sourceClass, handle.getDesc(), override);
    }

    private static void dynamic(Map<String, TypeDep> deps, String sourceClass, ConstantDynamic dynamic,
                                String override) {
        addType(deps, sourceClass, Type.getType(dynamic.getDescriptor()), override);
        handle(deps, sourceClass, dynamic.getBootstrapMethod(), override);
        for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) {
            Object arg = dynamic.getBootstrapMethodArgument(i);
            if (arg instanceof Type)
                addType(deps, sourceClass, (Type)arg, override);
            else if (arg instanceof Handle)
                handle(deps, sourceClass, (Handle)arg, override);
            else if (arg instanceof ConstantDynamic)
                dynamic(deps, sourceClass, (ConstantDynamic)arg, override);
        }
    }

    private static void addMethod(Map<String, TypeDep> deps, String sourceClass, String descriptor,
                                  String override) {
        Type method = Type.getMethodType(descriptor);
        addType(deps, sourceClass, method.getReturnType(), override);
        for (Type argument : method.getArgumentTypes())
            addType(deps, sourceClass, argument, override);
    }

    private static void addType(Map<String, TypeDep> deps, String sourceClass, Type type, String override) {
        if (type == null)
            return;
        if (type.getSort() == Type.ARRAY) {
            addType(deps, sourceClass, type.getElementType(), override);
        } else if (type.getSort() == Type.OBJECT) {
            add(deps, sourceClass, type.getInternalName(), override);
        } else if (type.getSort() == Type.METHOD) {
            addMethod(deps, sourceClass, type.getDescriptor(), override);
        }
    }

    private static void annotations(Map<String, TypeDep> deps, String sourceClass, List<AnnotationNode> annotations,
                                    String override) {
        if (annotations == null)
            return;
        for (AnnotationNode annotation : annotations) {
            addType(deps, sourceClass, Type.getType(annotation.desc), override);
            if (annotation.values == null)
                continue;
            for (int i = 1; i < annotation.values.size(); i += 2)
                annotationValue(deps, sourceClass, annotation.values.get(i), override);
        }
    }

    private static void signature(Map<String, TypeDep> deps, String sourceClass, String signature,
                                  String override, boolean classLevel) {
        if (signature == null || signature.isEmpty())
            return;
        SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
            private String current;

            @Override
            public void visitClassType(String name) {
                current = name;
                add(deps, sourceClass, name, override);
            }

            @Override
            public void visitInnerClassType(String name) {
                current = (current == null ? "" : current + "$") + name;
                add(deps, sourceClass, current, override);
            }
        };
        try {
            if (classLevel)
                new SignatureReader(signature).accept(visitor);
            else
                new SignatureReader(signature).acceptType(visitor);
        } catch (RuntimeException ignored) {
        }
    }

    private static void typeAnnotations(Map<String, TypeDep> deps, String sourceClass,
                                        List<? extends TypeAnnotationNode> annotations, String override) {
        if (annotations == null)
            return;
        for (TypeAnnotationNode annotation : annotations) {
            addType(deps, sourceClass, Type.getType(annotation.desc), override);
            if (annotation.values == null)
                continue;
            for (int i = 1; i < annotation.values.size(); i += 2)
                annotationValue(deps, sourceClass, annotation.values.get(i), override);
        }
    }

    private static void parameterAnnotations(Map<String, TypeDep> deps, String sourceClass,
                                             List<AnnotationNode>[] annotations, String override) {
        if (annotations == null)
            return;
        for (List<AnnotationNode> parameter : annotations) {
            if (parameter != null)
                annotations(deps, sourceClass, parameter, override);
        }
    }

    private static void localAnnotations(Map<String, TypeDep> deps, String sourceClass,
                                         List<LocalVariableAnnotationNode> annotations, String override) {
        typeAnnotations(deps, sourceClass, annotations, override);
    }

    private static void annotationValue(Map<String, TypeDep> deps, String sourceClass, Object value,
                                        String override) {
        if (value instanceof Type) {
            addType(deps, sourceClass, (Type)value, override);
        } else if (value instanceof AnnotationNode) {
            annotations(deps, sourceClass, Collections.singletonList((AnnotationNode)value), override);
        } else if (value instanceof List) {
            for (Object element : (List<?>)value)
                annotationValue(deps, sourceClass, element, override);
        } else if (value instanceof String[]) {
            addType(deps, sourceClass, Type.getObjectType(((String[])value)[0]), override);
        }
    }

    private static void add(Map<String, TypeDep> deps, String sourceClass, String owner, String override) {
        if (owner == null || owner.isEmpty() || owner.startsWith("["))
            return;
        TypeDep dep = new TypeDep(sourceClass, owner, override);
        deps.putIfAbsent(dep.key(), dep);
    }

    private static String overrideKey(ClassNode node) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.invisibleAnnotations != null)
            annotations.addAll(node.invisibleAnnotations);
        if (node.visibleAnnotations != null)
            annotations.addAll(node.visibleAnnotations);
        for (AnnotationNode annotation : annotations) {
            if (!COMPAT_RANGE.equals(annotation.desc) || annotation.values == null)
                continue;
            String floor = null, ceiling = null;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String)annotation.values.get(i);
                Object value = annotation.values.get(i + 1);
                if ("floor".equals(key))
                    floor = (String)value;
                else if ("ceiling".equals(key))
                    ceiling = (String)value;
            }
            return (floor == null ? "default" : floor) + "|" + (ceiling == null ? "default" : ceiling);
        }
        return null;
    }
}
