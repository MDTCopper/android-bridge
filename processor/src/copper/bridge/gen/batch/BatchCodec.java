package copper.bridge.gen.batch;

import copper.bridge.gen.Template;
import copper.bridge.gen.TypeRef;
import java.util.List;

/**
 * The one buffer a codec reads and writes through, so the little-endian implementation exists once in
 * the tested wire layer and a field is one call rather than a shift-and-mask written out per field.
 */
final class BatchCodec {
    /** The name the generated code knows that buffer by. */
    static final String BYTES = "wireBytes";

    private BatchCodec() {
    }

    /** The field declaration for that buffer. */
    static Template byteBuffer() {
        return Template.of("""
                /** Holds the values between the frame's bytes and the codec. */
                private final WireByteBuffer {{bytes}} = new WireByteBuffer();
                """).with("bytes", BYTES);
    }

    /**
     * Writes one field at the cursor the caller has already bound and made room for: the room is asked
     * for once per record rather than per field, and the bound limit doubles as a check that the two
     * agree.
     */
    static Template write(Field field) {
        // A sequence is a CharSequence, so the byte buffer writes its characters itself: no copy of
        // the run, and no second implementation of the count-then-elements layout.
        String call = field.isSequence() ? BYTES + ".putString(" + field.name + ")"
                : BYTES + "." + field.writeCall();
        return Template.of("""
                {{call}};
                """).with("call", call);
    }

    /** One scalar field read at the cursor, assigned to the caller's target. */
    static Template read(Field field, String target) {
        return Template.of("""
                {{target}} = {{bytes}}.{{call}};
                """).with("target", target).with("bytes", BYTES).with("call", field.readCall());
    }

    static Template sequenceRead(String field, int width) {
        return Template.of("""
                final int count = {{bytes}}.getInt();
                {{field}}.bind(blob, {{bytes}}.arrayOffset() + {{bytes}}.position(), count);
                {{bytes}}.skip(count * {{width}});
                """)
                .with("bytes", BYTES)
                .with("field", field)
                .with("width", width);
    }

    /**
     * The bytes a record or a header takes, as an expression the generated code evaluates: a constant
     * plus a term per string or sequence, whose width depends on their value.
     *
     * @param leading the bytes before the first field, which is the record id for a record
     */
    static String bytesOf(List<Field> fields, int leading) {
        int fixed = leading;
        StringBuilder text = new StringBuilder();
        for (Field field : fields) {
            if (field.isSequence())
                text.append(" + 4 + ").append(field.name).append(".limit() * ").append(field.sequence.width);
            else if (field.isString())
                text.append(" + 4 + ").append(field.name).append(".length() * 2");
            else
                fixed += TypeRef.size(field.scalar.code);
        }
        return fixed + text.toString();
    }
}
