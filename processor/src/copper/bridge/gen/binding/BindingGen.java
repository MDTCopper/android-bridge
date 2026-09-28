package copper.bridge.gen.binding;

import java.io.IOException;

/**
 * Writes the generated half of the binding layer: the two vocabulary headers, the tables, the walk over them,
 * and the calls native makes back into Java.
 */
final class BindingGen {
    private BindingGen() {
    }

    static void generate(BindingProcessor processor) throws IOException {
        processor.writeCpp("binding.h", BindingHeader.of());
        processor.writeCpp("internal/binding.h", BindingInternal.of());
        processor.writeCpp("binding.cpp", BindingTables.of(processor));
        processor.writeCpp("vmcall.h", VmCallHeader.of(processor));
        processor.writeCpp("vmcall.cpp", VmCallSource.of(processor));
    }
}
