#ifndef COPPER_BRIDGE_BUS_WAITERS_H
#define COPPER_BRIDGE_BUS_WAITERS_H

#include "bus/message.h"

#include <jni.h>

// Waiting for an answer, and what happens to a caller that is still waiting.
//
// A synchronous row blocks the calling thread on this request's own waiter, and the side that performs the
// call completes it. There is no timeout, so every path that drops the message has to complete the waiter
// with a neutral value instead: a wait with no deadline and no release is a hang, not a slow call. Release
// is the entry point the generated ART table registers, for the moment the pump stops.

namespace copper::bridge::bus::Waiters {

    /** Queues a synchronous message and blocks on this request's own waiter until the peer answers.
     *  There is no timeout: a wait of five seconds logs one line and keeps waiting. A call whose target
     *  pump has already stopped is not queued at all, and answers with the neutral value. */
    void EnqueueAndWait(jni::Side to, Message&& message, jvalue& answer);

    /** Completes every waiter that is still blocked, with the neutral value. */
    void Release(JNIEnv*, jclass);

} // namespace copper::bridge::bus::Waiters

#endif // COPPER_BRIDGE_BUS_WAITERS_H
