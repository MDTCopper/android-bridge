package copper.bridge.gen.bus;

import java.io.IOException;

/** Writes the C++ half of the bus: the generated header, the row tables, and one stub per signature. */
final class CppGen {
    private CppGen() {
    }

    static void generate(BusProcessor processor) throws IOException {
        processor.writeCpp("bus.h", CppHeader.of(processor));
        processor.writeCpp("bus_tables.cpp", CppTables.of(processor));
        processor.writeCpp("bus_stubs.cpp", CppStubs.of(processor));
        processor.writeCpp("internal/bus.h", CppInternal.of(processor));
    }
}
