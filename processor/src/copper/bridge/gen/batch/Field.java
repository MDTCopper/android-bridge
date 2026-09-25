package copper.bridge.gen.batch;

import copper.bridge.gen.TypeRef;
import copper.bridge.gen.WireType;
import java.util.ArrayList;
import java.util.List;

/** One field of a schema's header. */
final class Field {
    /** The field name, which is also the name of the generated sender-side field. */
    String name;
    /** A scalar from the bridge's vocabulary, or {@code null} for a sequence. */
    TypeRef scalar;
    /** The sequence's buffer type, or {@code null} for a scalar. */
    WireType sequence;
    /** The source forms the declaration listed, as simple type names; a header has exactly one. */
    final List<String> forms = new ArrayList<>();

    boolean isSequence() {
        return sequence != null;
    }

    /** The Java type as a declaration writes it. */
    String java() {
        return isSequence() ? sequence.simple : scalar.java;
    }

    /** Whether this is a string, which is a scalar whose width depends on the value it carries. */
    boolean isString() {
        return !isSequence() && "L".equals(scalar.code);
    }

    /** The call the byte buffer is written with; a {@code boolean} travels as a byte. */
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

    /** The call the byte buffer is read with. */
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
