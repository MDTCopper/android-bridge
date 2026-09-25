#include "bus/message.h"

#include "jni/env.h"
#include "bus/internal/row.h"
#include "bus/internal/type_codes.h"
#include "util/log.h"

// The message's own two operations: the move that hands a payload over, and the release that gives it back.
//
// Both live here because they are the only operations that have to agree about which of the two payloads
// is the live one.

namespace copper::bridge::bus {
    namespace Bus = gen::Bus;

Message::Message(Message&& from) noexcept : kind(from.kind), requestId(from.requestId), refs{} {
    for (int i = 0; i < Bus::MAX_ARGS; i++)
        args[i] = from.args[i];

    if (Row::IsBoxed(from.kind)) {
        box = from.box;
        from.box = Box{};
    } else {
        for (int i = 0; i < Bus::MAX_REFS; i++)
            refs[i] = from.refs[i];
    }
}

Message& Message::operator=(Message&& from) noexcept {
    kind = from.kind;
    requestId = from.requestId;
    for (int i = 0; i < Bus::MAX_ARGS; i++)
        args[i] = from.args[i];

    if (Row::IsBoxed(from.kind)) {
        box = from.box;
        from.box = Box{};
    } else {
        for (int i = 0; i < Bus::MAX_REFS; i++)
            refs[i] = from.refs[i];
    }
    return *this;
}

void Message::Dispose() {
    const Bus::CallEntry* entry = Bus::FindCall(kind);
    if (entry == nullptr)
        return;

    if (entry->return_code != 'V') {
        const int count = TypeCodes::ObjectCount(entry->param_codes);
        if (count > 0) {
            jni::Env targetEnv(entry->target);
            JNIEnv* env = targetEnv.Get();
            if (env != nullptr) {
                for (int i = 0; i < count; i++) {
                    if (refs[i] != nullptr) {
                        env->DeleteGlobalRef(refs[i]);
                        refs[i] = nullptr;
                    }
                }
            }
        }
        return;
    }

    if (box.data != nullptr) {
        delete[] box.data;
        box = Box{};
    }
}

} // namespace copper::bridge::bus
