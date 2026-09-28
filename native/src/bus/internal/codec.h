#ifndef COPPER_BRIDGE_BUS_INTERNAL_CODEC_H
#define COPPER_BRIDGE_BUS_INTERNAL_CODEC_H

#include "bus/codec.h"

// Reading a message back: writing is public, reading has one caller, the thread that performs the row.

namespace copper::bridge::bus::Codec {

    /** The other half of the scalar codec: unpacks one value out of the message's int slots. */
    jvalue ReadScalar(const jint* args, int& cursor, char code);

    void DecodeBoxed(JNIEnv* env, const Message& message, jobject* out);

} // namespace copper::bridge::bus::Codec

#endif // COPPER_BRIDGE_BUS_INTERNAL_CODEC_H
