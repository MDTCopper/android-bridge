package copper.bridge.compat.api;

import org.objectweb.asm.*;

import java.util.*;

/** The declarations and supertypes of one class file. */
public final class ClassInfo {
    public final String name;
    public final int access;
    public final String superName;
    public final String[] interfaces;
    private final Map<String, MemberInfo> members = new HashMap<>();

    public ClassInfo(String name, int access, String superName, String[] interfaces) {
        this.name = name;
        this.access = access;
        this.superName = superName;
        this.interfaces = interfaces == null ? new String[0] : interfaces;
    }

    void add(MemberInfo member) {
        members.put(member.name + member.descriptor, member);
    }

    /** The member with this exact name and descriptor, or null. */
    public MemberInfo member(String name, String descriptor) {
        return members.get(name + descriptor);
    }

    /** Whether any declaration has this name; reflection names a member without its descriptor. */
    public boolean hasMemberNamed(String name) {
        for (MemberInfo member : members.values()) {
            if (member.name.equals(name))
                return true;
        }
        return false;
    }

    /** Reads one class file without code, debug data or frames. */
    public static ClassInfo parse(byte[] bytes) {
        Collector collector = new Collector();
        new ClassReader(bytes).accept(collector,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return collector.info;
    }

    private static final class Collector extends ClassVisitor {
        ClassInfo info;

        Collector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            info = new ClassInfo(name, access, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature,
                                       Object value) {
            info.add(new MemberInfo(name, descriptor, access));
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            info.add(new MemberInfo(name, descriptor, access));
            return null;
        }
    }
}
