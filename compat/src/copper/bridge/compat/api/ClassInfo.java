package copper.bridge.compat.api;

import java.util.*;

/** The declarations of one class file. */
public final class ClassInfo {
    public final String name;
    public final int access;
    private final Map<String, MemberInfo> members = new HashMap<>();

    ClassInfo(String name, int access) {
        this.name = name;
        this.access = access;
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
}
