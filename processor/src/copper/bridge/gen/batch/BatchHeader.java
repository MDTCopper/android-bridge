package copper.bridge.gen.batch;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code gen/batch.h}: the table of channels and their bounds, generated so the ids native reads cannot
 * drift from the ids Java sends.
 */
final class BatchHeader {
    private BatchHeader() {
    }

    static String of(BatchProcessor processor) {
        List<Template> constants = new ArrayList<>();
        for (Schema schema : processor.schemas())
            constants.add(Template.of("""
                    constexpr int32_t {{constant}} = {{id}};
                    """).with("constant", schema.constant()).with("id", schema.id));

        List<Template> entries = new ArrayList<>();
        for (Schema schema : processor.schemas())
            entries.add(Template.of("""
                    { {{constant}}, "{{accessor}}", jni::Side::{{side}}, {{batches}}, {{bytes}} },
                    """)
                    .with("constant", schema.constant())
                    .with("accessor", schema.accessor)
                    .with("side", schema.receiver.cpp)
                    .with("batches", schema.maxBatches)
                    .with("bytes", schema.maxBytes));

        // Includes the hand-written side vocabulary, not the other system's generated header.
        return Template.of("""
                // Generated. Do not edit.
                #pragma once

                #include <cstdint>

                #include "jni/side.h"

                // The channels. The ring and the two entry points every channel shares are hand written; the ids
                // native uses are generated here so they cannot drift from the ids Java sends.
                namespace copper::bridge::gen::Batch {

                /** One channel: its id, the side that receives, and its two bounds. */
                struct BatchEntry {
                    int32_t id;
                    const char* name;
                    jni::Side direction;
                    int32_t maxBatches;
                    int32_t maxBytes;
                };

                constexpr int BATCH_COUNT = {{count}};

                {{constants}}

                inline constexpr BatchEntry BATCH_ENTRIES[BATCH_COUNT] = {
                        {{entries}}
                };

                }  // namespace copper::bridge::gen::Batch
                """)
                .with("count", processor.schemas().size())
                .with("constants", Template.join(constants, "\n"))
                .with("entries", Template.join(entries, "\n"))
                .render();
    }
}
