#ifndef COPPER_BRIDGE_BUS_CODEC_H
#define COPPER_BRIDGE_BUS_CODEC_H

#include "bus/message.h"

#include <jni.h>

// The payload codec: what a row's parameters look like on the way across.
//
// A row carries one of two payloads and they never mix. A synchronous row has its objects built in the
// target VM and kept as global references, so the handler gets a ready argument; an asynchronous row has
// them encoded in the sender's VM and materialised when the peer pumps, which keeps the enqueuing thread
// out of the other VM entirely. Scalars go into the message's int slots either way.

namespace copper::bridge::bus::Codec {

    /** Encodes the payload of a row into its message, per the row's own type codes. */
    void PutPayload(JNIEnv* from, Message& message, const jvalue* values, int count);

} // namespace copper::bridge::bus::Codec

#endif // COPPER_BRIDGE_BUS_CODEC_H
