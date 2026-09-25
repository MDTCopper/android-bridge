package copper.bridge.gen.bus;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code gen/bus_stubs.cpp}: one function per (parameters, return) signature, shared by both sides.
 *
 * <p>A stub is what the JVM's {@code RegisterNatives} binds, so nothing in native is written once per
 * kind.</p>
 */
final class CppStubs {
    private CppStubs() {
    }

    static String of(BusProcessor processor) {
        List<Template> stubs = new ArrayList<>();
        for (Map.Entry<String, Row> shape : Row.shapes(processor.rows()).entrySet())
            stubs.add(stub(shape.getValue().entry(), shape.getValue()));

        return Template.of("""
                // Generated. Do not edit. One stub per (parameters, return) signature, shared by both
                // sides: which queue a call goes to, and whose handler runs, comes from the row's channel.
                #include "bus.h"
                #include "internal/bus.h"
                #include "bus/codec.h"
                #include "bus/dispatch.h"
                #include "bus/mailbox.h"
                #include "bus/message.h"
                #include "bus/waiters.h"
                #include <utility>

                namespace copper::bridge::gen::Bus {

                {{stubs}}

                }  // namespace copper::bridge::gen::Bus
                """)
                .with("stubs", Template.join(stubs, "\n\n"))
                .render();
    }

    /**
     * The names in a stub are positions, not the parameter names of whichever declaration asked for the
     * signature: no single declaration owns the shape.
     */
    private static Template stub(String entry, Row row) {
        boolean returns = !row.result.isVoid;
        List<Template> queued = new ArrayList<>();
        if (!row.params.isEmpty())
            queued.add(put(row, 0, row.params.size()));
        queued.add(enqueue(row));

        return Template.of("""
                /// {{name}} and every other row of the same signature.
                {{result}} {{entry}}(JNIEnv* env, jclass, jint kind{{params}}) {
                    const Kind k = static_cast<Kind>(kind);
                    if (FindDirect(k) != nullptr) {
                        {{arguments}}
                        {{answer}}
                        bus::Dispatch::InvokeDirect(env, k, {{payload}}, {{directAnswer}});
                        {{directReturn}}
                    }
                    bus::Message queued{k};
                    {{leading}}
                    {{queue}}
                }
                """)
                .with("name", row.name)
                .with("result", row.result.jni)
                .with("entry", entry)
                .with("params", row.jniParameters())
                .with("arguments", row.params.isEmpty() ? "" : arguments(row).render())
                .with("answer", returns ? "jvalue answer{};" : "")
                .with("payload", row.params.isEmpty() ? "nullptr, 0" : "arguments, " + row.params.size())
                .with("directAnswer", returns ? "&answer" : "nullptr")
                .with("directReturn", returns ? "return " + row.jniAnswer() + ";" : "return;")
                .with("leading", row.leadingRequest ? leading(row).render() : "")
                .with("queue", Template.join(queued, "\n"));
    }

    /** The request-id branch a row with a leading request id takes, ahead of the ordinary payload. */
    private static Template leading(Row row) {
        List<Template> queued = new ArrayList<>();
        if (row.params.size() > 1)
            queued.add(put(row, 1, row.params.size()));
        queued.add(enqueue(row));

        return Template.of("""
                if (TakesRequest(k)) {
                    queued.requestId = {{request}};
                    {{put}}
                    {{enqueue}}
                    return;
                }
                """)
                .with("request", row.argNames().get(0))
                .with("put", row.params.size() > 1 ? put(row, 1, row.params.size()).render() : "")
                .with("enqueue", enqueue(row).render());
    }

    /** The slots a direct call passes, one per parameter. */
    private static Template arguments(Row row) {
        List<Template> slots = new ArrayList<>();
        for (int i = 0; i < row.params.size(); i++)
            slots.add(Template.of("""
                    arguments[{{index}}].{{jvalue}} = {{name}};
                    """)
                    .with("index", i)
                    .with("jvalue", row.params.get(i).jvalue)
                    .with("name", row.argNames().get(i)));

        return Template.of("""
                jvalue arguments[{{count}}];
                {{slots}}""")
                .with("count", row.params.size())
                .with("slots", Template.join(slots, "\n"));
    }

    /** The slots a queued call carries, from one parameter up to the end of the list. */
    private static Template put(Row row, int from, int to) {
        List<Template> slots = new ArrayList<>();
        for (int i = from; i < to; i++)
            slots.add(Template.of("""
                    values[{{index}}].{{jvalue}} = {{name}};
                    """)
                    .with("index", i - from)
                    .with("jvalue", row.params.get(i).jvalue)
                    .with("name", row.argNames().get(i)));

        return Template.of("""
                jvalue values[{{count}}];
                {{slots}}
                bus::Codec::PutPayload(env, queued, values, {{count}});
                """)
                .with("count", to - from)
                .with("slots", Template.join(slots, "\n"));
    }

    /** What happens to the message once it is built: a void row hands it over, any other waits. */
    private static Template enqueue(Row row) {
        if (row.result.isVoid)
            return Template.of("""
                    bus::Mailbox::Enqueue(TargetOf(k), std::move(queued));
                    """);
        return Template.of("""
                jvalue answer{};
                bus::Waiters::EnqueueAndWait(TargetOf(k), std::move(queued), answer);
                return {{answer}};
                """).with("answer", row.jniAnswer());
    }
}
