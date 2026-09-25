package copper.bridge.gen.bus;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code gen/internal/bus.h}: the row lookups the walk and the stubs use, and the stubs themselves.
 *
 * <p>None of it leaves the module, so it is not in the public header.</p>
 */
final class CppInternal {
    private CppInternal() {
    }

    static String of(BusProcessor processor) {
        List<Template> stubs = new ArrayList<>();
        for (Map.Entry<String, Row> shape : Row.shapes(processor.rows()).entrySet()) {
            Row row = shape.getValue();
            stubs.add(Template.of("""
                    {{result}} {{entry}}(JNIEnv*{{params}});
                    """)
                    .with("result", row.result.jni)
                    .with("entry", row.entry())
                    .with("params", row.jniSignature()));
        }

        return Template.of("""
                // Generated. Do not edit.
                #pragma once

                #include <jni.h>

                #include "gen/bus.h"

                namespace copper::bridge::gen::Bus {

                /** The request an answer row belongs to, or Kind::None. */
                Kind RequestOf(Kind kind);
                /** The side that owns this row's handler, so a caller knows whose queue and env to use. */
                jni::Side TargetOf(Kind kind);
                /** Whether this kind's first parameter is the request id. */
                bool TakesRequest(Kind kind);

                // The stubs themselves, one per (parameters, return) signature. Each side's bus class registers
                // the ones it declares, together with the bus's own hand-written entries.
                {{stubs}}

                } // namespace copper::bridge::gen::Bus
                """)
                .with("stubs", Template.join(stubs, "\n"))
                .render();
    }
}
