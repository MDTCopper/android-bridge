#ifndef COPPER_BRIDGE_BUS_MESSAGE_H
#define COPPER_BRIDGE_BUS_MESSAGE_H

#include "gen/bus.h"

#include <jni.h>

#include <cstdint>

// One queued call: what it is and what it carries. A synchronous row holds objects built in the target VM as global
// references; an asynchronous one encodes them in the sender's VM. The payloads share storage, so the message is
// move-only.

namespace copper::bridge::bus {

/** The boxed payload of one asynchronous row: one exactly sized block laid out as
 *    [u8 count][u32 len0][value0][u32 len1][value1]...
 *  `count` is the number of object parameters, each `len` an element count; every length is known before encoding. */
struct Box {
    uint8_t* data = nullptr;
    uint32_t size = 0;
};

struct Message {
    gen::Bus::Kind kind = gen::Bus::Kind::None;
    int64_t requestId = 0;
    jint args[gen::Bus::MAX_ARGS] = {};
    union {
        jobject refs[gen::Bus::MAX_REFS];
        Box box;
    };

    Message() : refs{} {
    }

    explicit Message(gen::Bus::Kind row) : kind(row), refs{} {
    }

    ~Message() = default;
    Message(const Message&) = delete;
    Message& operator=(const Message&) = delete;
    Message(Message&& from) noexcept;
    Message& operator=(Message&& from) noexcept;

    /** Gives back what this message holds. Explicit, not a destructor: releasing a global reference needs the
     *  owning side's environment. */
    void Dispose();
};

} // namespace copper::bridge::bus

#endif // COPPER_BRIDGE_BUS_MESSAGE_H
