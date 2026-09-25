#ifndef COPPER_BRIDGE_BUS_INTERNAL_CODEC_H
#define COPPER_BRIDGE_BUS_INTERNAL_CODEC_H

#include "bus/codec.h"

// Reading a message back. Writing is public because a caller hands a payload over; reading has one caller,
// the thread that performs the row, so it stays inside the bus.

namespace copper::bridge::bus::Codec {

    /** The other half of the scalar codec: unpacks one value out of the message's int slots. */
    jvalue ReadScalar(const jint* args, int& cursor, char code);

    /** Materialises the objects of an asynchronous row in this environment, in parameter order. */
    void DecodeBoxed(JNIEnv* env, const Message& message, jobject* out);

} // namespace copper::bridge::bus::Codec

#endif // COPPER_BRIDGE_BUS_INTERNAL_CODEC_H
