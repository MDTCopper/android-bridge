#ifndef COPPER_BRIDGE_BUS_MESSAGE_H
#define COPPER_BRIDGE_BUS_MESSAGE_H

#include "gen/bus.h"

#include <jni.h>

#include <cstdint>

// One queued call: what it is and what it carries.
//
// A message is either synchronous - the objects it carries already live in the target VM, held as global
// references - or asynchronous, where they are encoded in the sender's VM and materialised when the peer
// pumps. The two payloads are mutually exclusive and share storage, and both are trivial, so the union
// needs no hand-written special members. The message is move-only, because a box owns a raw block.

namespace copper::bridge::bus {

/** The boxed payload of one asynchronous row: a single, exactly sized block laid out as
 *    [u8 count][u32 len0][value0][u32 len1][value1]...
 *  `count` is the number of object parameters and each `len` an element count whose byte span follows
 *  from the type code. Every length is known before encoding, so the block is measured, allocated and
 *  written once. */
struct Box {
    uint8_t* data = nullptr;
    uint32_t size = 0;
};

/** One queued message. */
struct Message {
    gen::Bus::Kind kind = gen::Bus::Kind::None;
    /** The request id: an asynchronous call's holder, or a synchronous call's waiter. */
    int64_t requestId = 0;
    /** Scalar payload, one jint slot each; a long or a double is two, low half first. */
    jint args[gen::Bus::MAX_ARGS] = {};
    union {
        /** Synchronous rows: objects built in the target VM and held as global references. */
        jobject refs[gen::Bus::MAX_REFS];
        /** Asynchronous rows: objects encoded in the sender's VM. */
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

    /** Gives back what this message holds: the global references, or the box.
     *
     *  <p>Called explicitly rather than from a destructor: which payload is live comes from the generated
     *  row, releasing a global reference needs the owning side's environment, and these messages outlive
     *  every VM in the process.</p>
     */
    void Dispose();
};

} // namespace copper::bridge::bus

#endif // COPPER_BRIDGE_BUS_MESSAGE_H
