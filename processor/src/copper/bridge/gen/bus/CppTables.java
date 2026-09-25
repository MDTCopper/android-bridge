package copper.bridge.gen.bus;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code gen/bus_tables.cpp}: what the hand-written bus reads to find a row's handler.
 *
 * <p>The kind-id-to-index maps are emitted from the same rows as the tables, so the two cannot disagree
 * about which row an id means.</p>
 */
final class CppTables {
    private CppTables() {
    }

    static String of(BusProcessor processor) {
        List<Row> calls = new ArrayList<>();
        List<Row> directs = new ArrayList<>();
        for (Row row : processor.rows()) {
            if (row.channel.pumped())
                calls.add(row);
            else
                directs.add(row);
        }

        List<Template> callIndex = new ArrayList<>();
        List<Template> directIndex = new ArrayList<>();
        for (Row row : processor.rows()) {
            callIndex.add(Template.of("""
                    {{index}},
                    """).with("index", calls.indexOf(row)));
            directIndex.add(Template.of("""
                    {{index}},
                    """).with("index", directs.indexOf(row)));
        }

        List<Template> callEntries = new ArrayList<>();
        for (Row row : calls)
            callEntries.add(Template.of("""
                    { Kind::{{kind}}, Channel::{{channel}}, jni::Side::{{side}}, "{{owner}}", "{{method}}", "{{descriptor}}", "{{codes}}", '{{return}}', {{isStatic}}, {{leading}} },
                    """)
                    .with("kind", row.enumName)
                    .with("channel", row.channel.cpp)
                    .with("side", row.ownerSide.cpp)
                    .with("owner", row.owner)
                    .with("method", row.method)
                    .with("descriptor", row.descriptor)
                    .with("codes", row.payloadCodes())
                    .with("return", row.result.code)
                    .with("isStatic", row.isStatic ? "true" : "false")
                    .with("leading", row.leadingRequest ? "true" : "false"));

        List<Template> directEntries = new ArrayList<>();
        for (Row row : directs)
            directEntries.add(Template.of("""
                    { Kind::{{kind}}, jni::Side::{{side}}, "{{owner}}", "{{method}}", "{{descriptor}}", "{{codes}}", '{{return}}' },
                    """)
                    .with("kind", row.enumName)
                    .with("side", row.ownerSide.cpp)
                    .with("owner", row.owner)
                    .with("method", row.method)
                    .with("descriptor", row.descriptor)
                    .with("codes", row.payloadCodes())
                    .with("return", row.result.code));

        List<Template> names = new ArrayList<>();
        for (Row row : processor.rows())
            names.add(Template.of("""
                    case Kind::{{kind}}: return "{{name}}";
                    """).with("kind", row.enumName).with("name", row.name));

        List<Template> requests = new ArrayList<>();
        for (Row row : processor.rows()) {
            if (row.channel != Channel.POST || row.callbacks.isEmpty())
                continue;
            List<Template> answers = new ArrayList<>();
            for (Callback callback : row.callbacks)
                answers.add(Template.of("""
                        case Kind::{{kind}}:
                        """).with("kind", callback.answer.enumName));
            requests.add(Template.of("""
                    {{answers}}
                        return Kind::{{request}};
                    """)
                    .with("answers", Template.join(answers, "\n"))
                    .with("request", row.enumName));
        }

        return Template.of("""
                // Generated. Do not edit.
                #include "bus.h"

                #include "internal/bus.h"

                // The bus's four hand-written entry points, which the two tables at the bottom register.
                // Each is declared beside the file that implements it, so nothing declares them twice.
                #include "bus/dispatch.h"
                #include "bus/handlers.h"
                #include "bus/waiters.h"

                namespace copper::bridge::gen::Bus {
                namespace {

                /** Kind id to its index in CALL_ENTRIES, or -1. Emitted so a lookup costs one array
                 *  read instead of a scan. */
                constexpr int8_t CALL_INDEX[KIND_COUNT] = {
                        {{callIndex}}
                };

                /** Kind id to its index in DIRECT_ENTRIES, or -1. */
                constexpr int8_t DIRECT_INDEX[KIND_COUNT] = {
                        {{directIndex}}
                };

                // How many rows each table has, and the tables themselves: the two lookups above and the walk
                // are their only readers, so none of these names belongs in the header.
                constexpr int CALL_COUNT = {{callCount}};
                constexpr int DIRECT_COUNT = {{directCount}};

                const CallEntry CALL_ENTRIES[CALL_COUNT] = {
                        {{callEntries}}
                };

                const DirectEntry DIRECT_ENTRIES[DIRECT_COUNT] = {
                        {{directEntries}}
                };

                }  // namespace

                const char* NameOf(Kind kind) {
                    switch (kind) {
                        {{names}}
                        case Kind::None: return "none";
                        default: return "unknown";
                    }
                }

                Kind RequestOf(Kind kind) {
                    switch (kind) {
                        {{requests}}
                        default: return Kind::None;
                    }
                }

                const CallEntry* FindCall(Kind kind) {
                    const int id = static_cast<int>(kind);
                    if (id < 0 || id >= KIND_COUNT || CALL_INDEX[id] < 0)
                        return nullptr;
                    return &CALL_ENTRIES[CALL_INDEX[id]];
                }

                const DirectEntry* FindDirect(Kind kind) {
                    const int id = static_cast<int>(kind);
                    if (id < 0 || id >= KIND_COUNT || DIRECT_INDEX[id] < 0)
                        return nullptr;
                    return &DIRECT_ENTRIES[DIRECT_INDEX[id]];
                }

                bool TakesRequest(Kind kind) {
                    const CallEntry* entry = FindCall(kind);
                    return entry != nullptr && entry->leading_request;
                }

                jni::Side TargetOf(Kind kind) {
                    if (const DirectEntry* direct = FindDirect(kind))
                        return direct->target;
                    if (const CallEntry* call = FindCall(kind))
                        return call->target;
                    return jni::Side::Art;
                }

                } // namespace copper::bridge::gen::Bus
                """)
                .with("callIndex", Template.join(callIndex, "\n"))
                .with("directIndex", Template.join(directIndex, "\n"))
                .with("callCount", calls.size())
                .with("directCount", directs.size())
                .with("callEntries", Template.join(callEntries, "\n"))
                .with("directEntries", Template.join(directEntries, "\n"))
                .with("names", Template.join(names, "\n"))
                .with("requests", Template.join(requests, "\n"))
                .render();
    }
}
