#ifndef COPPER_BRIDGE_BUS_WAITERS_H
#define COPPER_BRIDGE_BUS_WAITERS_H

#include "bus/message.h"

#include <jni.h>

// Waiting for an answer: there is no timeout, so every path that drops the message has to complete the waiter with a
// neutral value instead; a wait with no deadline and no release is a hang, not a slow call.

namespace copper::bridge::bus::Waiters {

    void EnqueueAndWait(jni::Side to, Message&& message, jvalue& answer);

    void Release(JNIEnv*, jclass);

} // namespace copper::bridge::bus::Waiters

#endif // COPPER_BRIDGE_BUS_WAITERS_H
