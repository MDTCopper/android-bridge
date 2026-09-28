package copper.bridge.gen.batch;

import copper.bridge.gen.Names;
import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the receiving half of one channel: the class a caller walks a frame with, by polling and then reading one
 * header and one call per record.
 */
final class BatchReader {
    private BatchReader() {
    }

    static Template of(Schema schema, String owner) {
        String accessor = Names.capitalize(schema.accessor);

        List<Template> cases = new ArrayList<>();
        for (Record record : schema.records) {
            List<Template> reads = new ArrayList<>();
            for (Field param : record.params) {
                if (param.isSequence())
                    reads.add(BatchCodec.sequenceRead(record.fieldName(param), param.sequence.width));
                else
                    reads.add(BatchCodec.read(param, "final " + param.scalar.java + " " + param.name));
            }
            cases.add(Template.of("""
                    case {{id}}: {
                        {{reads}}
                        target.{{record}}({{arguments}});
                        break;
                    }
                    """)
                    .with("id", record.id)
                    .with("reads", Template.join(reads, "\n"))
                    .with("record", record.name)
                    .with("arguments", record.wireArguments()));
        }

        List<Template> members = new ArrayList<>();
        for (Record record : schema.records) {
            for (Field param : record.params) {
                if (!param.isSequence())
                    continue;
                members.add(Template.of("""
                        /** Allocated once: the record binds it to each frame's block. */
                        private final {{type}} {{field}} = new {{type}}();
                        """).with("type", param.java()).with("field", record.fieldName(param)));
            }
        }
        members.add(Template.of("""
                private {{Accessor}}() {
                }
                """).with("Accessor", accessor));

        return Template.of("""
                    /** The iteration of the '{{accessor}}' channel: take the backlog, then walk it frame by frame. */
                    public static final {{Accessor}} {{accessor}} = new {{Accessor}}();

                    public static final class {{Accessor}} {
                        /** The block the last poll returned; every buffer below points into it. */
                        private byte[] blob = new byte[1 << 12];
                        private int end;
                        /** Where the frame being processed ends, which is how a bad one is skipped. */
                        private int frameEnd;

                        {{buffers}}
                        /** Takes every frame that arrived, in one crossing. */
                        public void poll() {
                            blob = {{owner}}.poll({{constant}}, blob);
                            {{bytes}}.bind(blob, 0, blob.length);
                            end = {{bytes}}.getInt() + 4;
                            if (end > blob.length)
                                end = blob.length;
                        }

                        /** Whether another frame is waiting in the block. */
                        public boolean hasNext() {
                            return {{bytes}}.position() < end;
                        }

                        /** Reads this frame's header into the bound instance's fields. */
                        public void processHeader() {
                            final {{type}} target = bound{{Accessor}};
                            if (target == null) {
                                Log.warn("bridge: batch channel {{accessor}} is not bound, dropping the frame");
                                {{bytes}}.position(end);
                                return;
                            }
                            try {
                                final int start = {{bytes}}.position();
                                frameEnd = start + 4 + {{bytes}}.getInt();
                            } catch (BatchFormatException e) {
                                Log.warn("bridge: batch frame length out of range ({{accessor}})");
                                {{bytes}}.position(end);
                                return;
                            }
                            if (frameEnd > end) {
                                Log.warn("bridge: batch frame out of range");
                                {{bytes}}.position(end);
                                return;
                            }
                            {{header}}
                        }

                        /** Calls the bound instance once per record, in the order they arrived. */
                        public void processRecords() {
                            final {{type}} target = bound{{Accessor}};
                            if (target == null)
                                return;
                            while ({{bytes}}.position() < frameEnd) {
                                final int id = {{bytes}}.get() & 0xFF;
                                try {
                                    switch (id) {
                                        {{cases}}
                                        default:
                                            throw new BatchFormatException("record " + id
                                                    + " is not in the schema");
                                    }
                                } catch (BatchFormatException e) {
                                    Log.warn("bridge: " + e.getMessage());
                                    {{bytes}}.position(frameEnd);
                                    return;
                                }
                            }
                        }

                        /** Moves to the next frame, skipping whatever this one did not read. */
                        public void next() {
                            {{bytes}}.position(frameEnd);
                        }

                        /** The whole backlog, with no hook between the header and its records. */
                        public void processAll() {
                            while (hasNext()) {
                                processHeader();
                                processRecords();
                                next();
                            }
                        }

                        {{members}}
                    }

                    """)
                .with("accessor", schema.accessor)
                .with("Accessor", accessor)
                .with("owner", owner)
                .with("constant", schema.constant())
                .with("type", Names.simpleName(schema.owner))
                .with("bytes", BatchCodec.BYTES)
                .with("buffers", BatchCodec.byteBuffer().render())
                .with("header", schema.headers.isEmpty() ? "" : headerRead(schema).render())
                .with("cases", Template.join(cases, "\n"))
                .with("members", Template.join(members, "\n\n"));
    }

    private static Template headerRead(Schema schema) {
        List<Template> reads = new ArrayList<>();
        for (Field field : schema.headers) {
            Template read = field.isSequence()
                    ? BatchCodec.sequenceRead(field.name, field.sequence.width)
                    : BatchCodec.read(field, "target." + field.name);
            reads.add(Template.of("""
                    {
                        {{read}}    }
                    """).with("read", read.render()));
        }

        return Template.of("""
                try {
                {{reads}}
                } catch (BatchFormatException e) {
                    Log.warn("bridge: batch field out of range ({{accessor}})");
                    {{bytes}}.position(frameEnd);
                }
                """)
                .with("reads", Template.join(reads, "\n"))
                .with("accessor", schema.accessor)
                .with("bytes", BatchCodec.BYTES);
    }
}
