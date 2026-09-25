package copper.bridge.gen.batch;

import copper.bridge.gen.Names;
import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the sending half of one channel: the class a caller writes a frame through. The wire form is
 * the receiving side's own signature, and every other source form the declaration listed becomes an
 * overload of the same name. The staging buffer is shared by this side's channels, so the control calls
 * carry the channel's suffix.
 */
final class BatchWriter {
    private BatchWriter() {
    }

    static Template of(Schema schema) {
        String accessor = Names.capitalize(schema.accessor);

        List<Template> headers = new ArrayList<>();
        for (Field header : schema.headers) {
            if (header.isSequence())
                headers.add(Template.of("""
                        /** Allocated once, here: a declaration never allocates. */
                        public final {{type}} {{name}} = new {{type}}();
                        """).with("type", header.java()).with("name", header.name));
            else
                headers.add(Template.of("""
                        public {{type}} {{name}};
                        """).with("type", header.scalar.java).with("name", header.name));
        }

        // One staging buffer for every value, one per sequence a source form is converted into: all
        // allocated here, so writing a frame allocates nothing.
        List<Template> buffers = new ArrayList<>();
        buffers.add(BatchCodec.byteBuffer());
        for (Record record : schema.records) {
            if (record.firstSourceForm() == null)
                continue;
            for (Field param : record.params) {
                if (!param.isSequence())
                    continue;
                buffers.add(Template.of("""
                        /** Holds the '{{record}}' text between the two forms. */
                        private final {{type}} {{field}} = new {{type}}();
                        """)
                        .with("record", record.name)
                        .with("type", param.java())
                        .with("field", record.fieldName(param)));
            }
        }

        List<Template> records = new ArrayList<>();
        for (Record record : schema.records)
            records.add(record(record));

        return Template.of("""
                    /** The data of the '{{accessor}}' channel: header fields and one method per record. */
                    public static final {{Accessor}} {{accessor}} = new {{Accessor}}();

                    public static final class {{Accessor}} {
                        {{headers}}
                        {{buffers}}

                        {{records}}

                        /**
                         * Writes this frame's header, once, in front of its first record.
                         *
                         * <p>The header has to be laid down before the records, and the records are
                         * written as the caller produces them, so this is called from every record method
                         * and does its work only while the frame is still empty. Submitting only rewinds the
                         * cursor, and a frame with no records is never sent at all.</p>
                         */
                        private void beginFrame() {
                            if (used != 0)
                                return;
                            {{header}}
                        }

                        private {{Accessor}}() {
                        }
                    }

                    /** Whether the '{{accessor}}' channel is paused: a submit while paused is dropped here, before native. */
                    private static boolean paused{{Accessor}};

                    /** Sends the frame written so far and rewinds the cursor. */
                    public static void submit{{Accessor}}() {
                        if (paused{{Accessor}}) {
                            used = 0;
                            return;
                        }
                        if (used > 0)
                            push({{constant}}, buffer, used);
                        used = 0;
                    }

                    /** Drops every frame submitted from now on, until it is set back. */
                    public static void setPaused{{Accessor}}(boolean value) {
                        paused{{Accessor}} = value;
                    }

                    {{lock}}
                    """)
                .with("accessor", schema.accessor)
                .with("Accessor", accessor)
                .with("headers", Template.join(headers, "\n"))
                .with("buffers", Template.join(buffers, "\n"))
                .with("records", Template.join(records, "\n"))
                .with("header", schema.headers.isEmpty() ? "" : header(schema).render())
                .with("constant", schema.constant())
                .with("lock", schema.withLock ? lock(accessor).render() : "");
    }

    /** The header write of a frame, once the room for it has been made. */
    private static Template header(Schema schema) {
        List<Template> writes = new ArrayList<>();
        for (Field field : schema.headers)
            writes.add(BatchCodec.write(field));

        return Template.of("""
                ensure({{size}});
                {{bytes}}.bind(buffer, used, {{size}});
                {{writes}}
                used += {{bytes}}.position();
                """)
                .with("size", BatchCodec.bytesOf(schema.headers, 0))
                .with("bytes", BatchCodec.BYTES)
                .with("writes", Template.join(writes, "\n"));
    }

    /** The lock a writer that is not alone takes, for one channel. */
    private static Template lock(String accessor) {
        return Template.of("""
                /**
                 * Takes the staging lock, for a writer that is not alone on this side.
                 *
                 * <p>Hold it across the records of one frame and the submit that sends it: the
                 * staging buffer is shared by this side's channels, and the ring a frame is pushed
                 * to has one producer.</p>
                 */
                public static void lock{{Accessor}}() {
                    LOCK.lock();
                }

                /** Releases what {@link #lock{{Accessor}}} took. */
                public static void unlock{{Accessor}}() {
                    LOCK.unlock();
                }

                """).with("Accessor", accessor);
    }

    /** One record's write methods. */
    private static Template record(Record record) {
        List<Template> writes = new ArrayList<>();
        for (Field param : record.params)
            writes.add(BatchCodec.write(param));

        List<Template> overloads = new ArrayList<>();
        for (List<String> signature : record.signatures) {
            if (record.sameAsWire(signature))
                continue;
            List<Template> sets = new ArrayList<>();
            for (Field param : record.params) {
                if (!param.isSequence())
                    continue;
                sets.add(Template.of("""
                        {{field}}.set({{name}});
                        """).with("field", record.fieldName(param)).with("name", param.name));
            }
            overloads.add(Template.of("""
                    /** The same record, from the source form the declaration lists. */
                    public void {{record}}({{params}}) {
                        {{sets}}
                        {{record}}({{arguments}});
                    }
                    """)
                    .with("record", record.name)
                    .with("params", record.sourceParameters(signature))
                    .with("sets", Template.join(sets, "\n"))
                    .with("arguments", record.wireArguments()));
        }

        Template form = Template.of("""
                /** Writes one '{{record}}' record. */
                public void {{record}}({{params}}) {
                    beginFrame();
                    ensure({{size}});
                    {{bytes}}.bind(buffer, used, {{size}});
                    {{bytes}}.put((byte) {{id}});
                    {{writes}}
                    used += {{bytes}}.position();
                }
                """)
                .with("record", record.name)
                .with("params", record.wireParameters())
                .with("size", BatchCodec.bytesOf(record.params, 1))
                .with("bytes", BatchCodec.BYTES)
                .with("id", record.id)
                .with("writes", Template.join(writes, "\n"));

        List<Template> forms = new ArrayList<>();
        forms.add(form);
        forms.addAll(overloads);

        return Template.of("""
                {{forms}}""").with("forms", Template.join(forms, "\n\n"));
    }
}
