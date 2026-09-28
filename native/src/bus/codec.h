#ifndef COPPER_BRIDGE_BUS_CODEC_H
#define COPPER_BRIDGE_BUS_CODEC_H

#include "bus/message.h"

#include <jni.h>

// The payload codec: a synchronous row's objects are built in the target VM and kept as global references; an
// asynchronous row's are encoded in the sender's VM, which keeps the enqueuing thread out of the other VM.

namespace copper::bridge::bus::Codec {

    /** Encodes the payload of a row into its message, per the row's own type codes. */
    void PutPayload(JNIEnv* from, Message& message, const jvalue* values, int count);

} // namespace copper::bridge::bus::Codec

#endif // COPPER_BRIDGE_BUS_CODEC_H
