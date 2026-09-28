package copper.bridge.gen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The buffer types a schema may declare a sequence as: the one shape both readable on the receiving side and
 * writable on the sending side, which is why a declaration names one instead of an array.
 */
public final class WireType {
    private static final Map<String, WireType> TYPES = new LinkedHashMap<>();

    static {
        add("WireByteBuffer", "b", "byte", 1);
        add("WireCharBuffer", "c", "char", 2);
        add("WireShortBuffer", "s", "short", 2);
        add("WireIntBuffer", "i", "int", 4);
        add("WireLongBuffer", "j", "long", 8);
        add("WireFloatBuffer", "f", "float", 4);
        add("WireDoubleBuffer", "d", "double", 8);
        // A string list is one view over the whole sequence: a buffer per string would allocate once
        // per string per frame.
        TYPES.put("WireStringView", new WireType("WireStringView", "l", "String", 0, true));
    }

    public final String simple;
    /** The type code from the bridge's closed vocabulary, so the two channels agree on types. */
    public final String code;
    public final String element;
    /** Bytes per element; zero for the string view, whose entries are variable. */
    public final int width;
    public final boolean strings;

    private WireType(String simple, String code, String element, int width, boolean strings) {
        this.simple = simple;
        this.code = code;
        this.element = element;
        this.width = width;
        this.strings = strings;
    }

    private static void add(String simple, String code, String element, int width) {
        TYPES.put(simple, new WireType(simple, code, element, width, false));
    }

    /**
     * The description of a declared type, or {@code null} when it is not a wire buffer. Matched on the simple name,
     * so a schema may import its buffers from anywhere.
     */
    public static WireType of(String type) {
        final int dot = type.lastIndexOf('.');
        return TYPES.get(dot < 0 ? type : type.substring(dot + 1));
    }

    public static String names() {
        return String.join(", ", TYPES.keySet());
    }
}
