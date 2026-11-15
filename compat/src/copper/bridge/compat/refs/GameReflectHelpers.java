package copper.bridge.compat.refs;

import java.util.*;

/**
 * The game's reflection helpers. This is a catalog, not a name guess: an unknown method on one of these owners is
 * reported instead of silently skipped. A helper the bridge owns is annotated with {@code @BridgeReflect} instead.
 */
final class GameReflectHelpers {

    static final class Helper {
        final ReflectionRef.Kind kind;
        final int classArg;
        final int memberArg;

        Helper(ReflectionRef.Kind kind, int classArg, int memberArg) {
            this.kind = kind;
            this.classArg = classArg;
            this.memberArg = memberArg;
        }
    }

    private static final Set<String> OWNERS = new HashSet<>(Arrays.asList(
            "arc/util/Reflect", "io/anuke/arc/util/Reflect"));
    private static final Map<String, Helper> HELPERS = new HashMap<>();

    static {
        for (String owner : OWNERS) {
            field(owner + ".set(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;)V", 0, 1);
            field(owner + ".set(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", 0, 2);
            field(owner + ".get(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;", 0, 1);
            field(owner + ".get(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", 0, 2);
            // The instance forms carry no Class token; the target cannot be resolved statically.
            field(owner + ".set(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", -1, 1);
            field(owner + ".get(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", -1, 1);
        }
    }

    private GameReflectHelpers() {
    }

    static boolean knownOwner(String owner) {
        return OWNERS.contains(owner);
    }

    static Helper find(String owner, String name, String descriptor) {
        return HELPERS.get(owner + "." + name + descriptor);
    }

    private static void field(String key, int classArg, int memberArg) {
        HELPERS.put(key, new Helper(ReflectionRef.Kind.FIELD, classArg, memberArg));
    }
}
