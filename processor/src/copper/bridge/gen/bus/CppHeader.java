package copper.bridge.gen.bus;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code gen/bus.h}: the row ids, the widths the tables were built to, and the two entry shapes. It declares
 * no JNI entry point: each is declared beside the file that implements it.
 */
final class CppHeader {
    private CppHeader() {
    }

    static String of(BusProcessor processor) {
        List<Template> kinds = new ArrayList<>();
        for (Row row : processor.rows())
            kinds.add(Template.of("""
                    {{name}} = {{id}},
                    """).with("name", row.enumName).with("id", row.id));

        return Template.of("""
                // Generated. Do not edit.
                #pragma once

                #include <jni.h>
                #include <cstdint>

                // This header declares the bus's rows inside the generated bus namespace, so a row has exactly one
                // name; the hand-written bus includes this header, never the other way round. The bus's own four JNI
                // entry points are not declared here - each is declared beside the file that implements it - and
                // neither are the generated stubs, which stay in internal/bus.h because a generated JNI function gets
                // no public header. The registration tables are written with every other table, in gen/binding.cpp.
                #include "jni/side.h"

                namespace copper::bridge::gen::Bus {

                /** One bus row. The ids come from sorting the constant names, so an unchanged
                 *  declaration keeps the id it always had. */
                enum class Kind : int32_t {
                    None = -1,
                    {{kinds}}
                };

                /** How many rows the table holds. */
                constexpr int KIND_COUNT = {{count}};

                /** Widest scalar payload of any row, in jint slots; a long or a double is two. */
                constexpr int MAX_ARGS = {{maxArgs}};

                /** Widest parameter list of any row, the leading request id included. */
                constexpr int MAX_PARAMS = {{maxParams}};

                /** Most object parameters on a synchronous row: the caller builds them in the target VM
                 *  and the message keeps that many global references. An asynchronous row encodes its
                 *  objects into the box instead, so it is not counted here. */
                constexpr int MAX_REFS = {{maxRefs}};

                /** Most object parameters on an asynchronous row. A box counts them in one byte. */
                constexpr int MAX_BOXED = {{maxBoxed}};

                /** The three channels that travel on a queue and are performed by the pump. */
                enum class Channel : int8_t { Post, Event, Answer };

                /** One row the pump performs. */
                struct CallEntry {
                    Kind kind;
                    Channel channel;
                    /** The side whose handler this row calls; the caller is the other one. */
                    jni::Side target;
                    const char* owner_class;
                    const char* method_name;
                    const char* descriptor;
                    /** One character per payload parameter; its length is the count. */
                    const char* param_codes;
                    /** 'V' normally; anything else on a Post row means the caller waits for it. */
                    char return_code;
                    bool is_static;
                    /** The first parameter is the request id: it travels as Message.requestId instead of
                     *  taking a payload slot. On an answer row it is the id the answer belongs to. */
                    bool leading_request;
                };

                /** One row performed on the calling thread, with no queue and no wake-up. */
                struct DirectEntry {
                    Kind kind;
                    /** The side whose handler this row calls; the caller is the other one. */
                    jni::Side target;
                    const char* owner_class;
                    const char* method_name;
                    const char* descriptor;
                    const char* param_codes;
                    char return_code;
                };

                const char* NameOf(Kind kind);
                /** The row the pump performs, or nullptr when the kind is a direct row. */
                const CallEntry* FindCall(Kind kind);
                /** The row performed on the calling thread, or nullptr when the pump performs it. */
                const DirectEntry* FindDirect(Kind kind);

                }  // namespace copper::bridge::gen::Bus
                """)
                .with("kinds", Template.join(kinds, "\n"))
                .with("count", processor.rows().size())
                .with("maxArgs", processor.maxArgs())
                .with("maxParams", processor.maxParams())
                .with("maxRefs", processor.maxRefs())
                .with("maxBoxed", processor.maxBoxed())
                .render();
    }
}
