package copper.bridge.gen.batch;

import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes one side's accessor class: the half of every channel that side takes part in.
 *
 * <p>Which half a side holds comes from the channel's own direction rather than from the side, so the
 * same class carries writers and readers for different channels. The two JNI entry points are one
 * declaration each rather than one per channel, because what crosses is the channel id plus the frame.</p>
 */
final class BatchAccessor {
    private BatchAccessor() {
    }

    static String of(BatchProcessor processor, Side side, String name) {
        List<Schema> writes = sent(processor, side);
        List<Schema> reads = received(processor, side);

        // Imported by the name each declaration carries: a schema may sit in any package, and naming a
        // package instead would be a claim about where declarations live.
        List<Template> imports = new ArrayList<>();
        for (Schema schema : processor.schemas())
            imports.add(Template.of("""
                    import {{owner}};
                    """).with("owner", schema.owner));
        imports.add(Template.of("""
                import {{native}};
                """).with("native", Packages.ANNOTATIONS + ".Native"));

        List<Template> constants = new ArrayList<>();
        for (Schema schema : processor.schemas())
            constants.add(Template.of("""
                    private static final int {{constant}} = {{id}};
                    """).with("constant", schema.constant()).with("id", schema.id));

        List<Template> writers = new ArrayList<>();
        for (Schema schema : writes)
            writers.add(BatchWriter.of(schema));

        List<Template> readers = new ArrayList<>();
        for (Schema schema : reads)
            readers.add(BatchReader.of(schema, name));

        List<Template> body = new ArrayList<>();
        if (!reads.isEmpty())
            body.add(BatchRegistry.of(reads));
        if (!writes.isEmpty())
            body.add(staging(writes));
        body.addAll(writers);
        body.add(Template.of("""
                /** Native: copies one frame into its channel's ring. */
                @Native("batch::Entry::Push")
                private static native void push(int channel, byte[] buffer, int length);
                """));
        body.addAll(readers);
        body.add(Template.of("""
                /** Native: takes every frame that arrived on this channel, in one crossing. */
                @Native("batch::Entry::Poll")
                private static native byte[] poll(int channel, byte[] reuse);
                """));
        body.add(Template.of("""
                private {{name}}() {
                }
                """).with("name", name));

        return Template.of("""
                package {{package}};

                {{imports}}
                import copper.bridge.util.*;
                import java.util.*;
                import java.util.concurrent.*;
                {{locks}}

                /** Generated. Do not edit. The {{side}} side of the batch channels: what this side writes, and what
                 * it receives. */
                public final class {{name}} {

                    {{constants}}

                    {{body}}
                }
                """)
                .with("package", Packages.GENERATED)
                .with("imports", Template.join(imports, "\n"))
                .with("locks", locked(writes) ? "import java.util.concurrent.locks.*;" : "")
                .with("side", side == Side.ART ? "ART" : "JVM")
                .with("name", name)
                .with("constants", Template.join(constants, "\n"))
                .with("body", Template.join(body, "\n\n"))
                .render();
    }

    /** The staging buffer every channel of one side is assembled in, and the room check for it. */
    private static Template staging(List<Schema> writes) {
        List<Template> parts = new ArrayList<>();
        parts.add(Template.of("""
                /** The staging buffer a frame is assembled in; it grows by doubling. */
                private static byte[] buffer = new byte[1 << 12];
                /** How much of it is written. */
                private static int used;
                """));
        if (locked(writes))
            parts.add(Template.of("""
                    /** The lock a withLock channel's writer holds across a frame. One per side,
                     * because the staging buffer is shared by this side's channels. */
                    private static final ReentrantLock LOCK = new ReentrantLock();

                    """));
        parts.add(Template.of("""
                /** Makes room for one more frame of at least this many bytes. */
                private static void ensure(int bytes) {
                    if ((long) used + bytes <= buffer.length)
                        return;
                    long wanted = Math.max((long) buffer.length * 2, (long) used + bytes);
                    if (wanted > Integer.MAX_VALUE - 8)
                        wanted = Integer.MAX_VALUE - 8;
                    if (wanted < (long) used + bytes)
                        throw new BatchFormatException("a frame of " + bytes + " bytes does not fit");
                    byte[] grown = new byte[(int) wanted];
                    System.arraycopy(buffer, 0, grown, 0, used);
                    buffer = grown;
                }
                """));

        return Template.of("""
                {{parts}}""").with("parts", Template.join(parts, "\n\n"));
    }

    /** The channels one side receives. */
    private static List<Schema> received(BatchProcessor processor, Side side) {
        return channels(processor, side);
    }

    /** The channels one side writes: the ones the other side receives. */
    private static List<Schema> sent(BatchProcessor processor, Side side) {
        return channels(processor, side.caller());
    }

    private static List<Schema> channels(BatchProcessor processor, Side receiver) {
        List<Schema> found = new ArrayList<>();
        for (Schema schema : processor.schemas()) {
            if (schema.receiver == receiver)
                found.add(schema);
        }
        return found;
    }

    /** Whether any channel of one side is declared withLock, which is what needs the staging lock. */
    private static boolean locked(List<Schema> writes) {
        for (Schema schema : writes) {
            if (schema.withLock)
                return true;
        }
        return false;
    }
}
