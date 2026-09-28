package copper.bridge.gen.bus;

import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
import copper.bridge.gen.Template;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Java half of the bus: the kind ids, one call class per side, and the holders of an asynchronous call's
 * outcomes.
 */
final class JavaGen {
    private JavaGen() {
    }

    static void generate(BusProcessor processor) throws IOException {
        processor.writeJava(Packages.GENERATED, "Kinds", JavaKinds.of(processor));
        processor.writeJava(Packages.GENERATED, "JvmCall", JavaCall.of(processor, Side.JVM));
        processor.writeJava(Packages.GENERATED, "ArtCall", JavaCall.of(processor, Side.ART));
        processor.writeJava(Packages.GENERATED, "ArtBus", JavaBus.of(processor, Side.ART));
        processor.writeJava(Packages.GENERATED, "JvmBus", JavaBus.of(processor, Side.JVM));
    }

    static List<Row> rowsFor(BusProcessor processor, Side side) {
        List<Row> rows = new ArrayList<>();
        for (Row row : processor.rows()) {
            if (row.callSide == side)
                rows.add(row);
        }
        return rows;
    }

    static Template importLine(String type) {
        return Template.of("""
                import {{type}};
                """).with("type", type);
    }
}
