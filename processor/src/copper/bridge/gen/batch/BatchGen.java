package copper.bridge.gen.batch;

import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes both halves of every batch channel: the accessors, the per-schema codec, and the C++ table of
 * channel ids. Both accessors come from the same model in one pass, so the writer's methods and the reader's
 * switch cannot disagree about a record id, a field order or a width.
 */
final class BatchGen {
    private BatchGen() {
    }

    static void generate(BatchProcessor processor) throws IOException {
        processor.writeJava(Packages.GENERATED, "ArtBatch", BatchAccessor.of(processor, Side.ART, "ArtBatch"));
        processor.writeJava(Packages.GENERATED, "JvmBatch", BatchAccessor.of(processor, Side.JVM, "JvmBatch"));
        processor.writeCpp("batch.h", BatchHeader.of(processor));
    }

    static List<Schema> sorted(List<Schema> schemas) {
        List<Schema> sorted = new ArrayList<>(schemas);
        sorted.sort((left, right) -> left.accessor.compareTo(right.accessor));
        return sorted;
    }
}
