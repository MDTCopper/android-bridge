package copper.bridge.gen.batch;

import copper.bridge.gen.TypeRef;
import copper.bridge.gen.WireType;
import java.util.ArrayList;
import java.util.List;

/** One field of a schema's header. */
final class Field {
    String name;
    TypeRef scalar;
    WireType sequence;
    /** The source forms the declaration listed, as simple type names; a header has exactly one. */
    final List<String> forms = new ArrayList<>();

    boolean isSequence() {
        return sequence != null;
    }

    String java() {
        return isSequence() ? sequence.simple : scalar.java;
    }

    boolean isString() {
        return !isSequence() && "L".equals(scalar.code);
    }

    String writeCall() {
        switch (scalar.code) {
            case "Z": return "put((byte) (" + name + " ? 1 : 0))";
            case "B": return "put(" + name + ")";
            case "C": return "putChar(" + name + ")";
            case "S": return "putShort(" + name + ")";
            case "J": return "putLong(" + name + ")";
            case "F": return "putFloat(" + name + ")";
            case "D": return "putDouble(" + name + ")";
            case "L": return "putString(" + name + ")";
            default: return "putInt(" + name + ")";
        }
    }

    String readCall() {
        switch (scalar.code) {
            case "Z": return "get() != 0";
            case "B": return "get()";
            case "C": return "getChar()";
            case "S": return "getShort()";
            case "J": return "getLong()";
            case "F": return "getFloat()";
            case "D": return "getDouble()";
            case "L": return "getString()";
            default: return "getInt()";
        }
    }
}
