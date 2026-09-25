package copper.bridge.gen.binding;

import java.io.IOException;

/**
 * Writes the generated half of the binding layer: the two vocabulary headers, the tables, the walk over
 * them, and the calls native makes back into Java.
 *
 * <p>What a table is belongs to {@code gen::Binding}, and every name in {@code gen::VmCall} is derived
 * from a Java member's, so nothing else may go there.</p>
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
