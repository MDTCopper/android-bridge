#ifndef COPPER_BRIDGE_BUS_INTERNAL_WAITERS_H
#define COPPER_BRIDGE_BUS_INTERNAL_WAITERS_H

#include "bus/waiters.h"

// Finishing one waiter, which is what the pump does for every synchronous call it performs. Releasing them
// all is private to waiters.cpp.

namespace copper::bridge::bus::Waiters {

    /** Completes the waiter registered under this id. An answer nobody waits for any more is released
     *  here, because only the environment it was created in can release it. */
    void Complete(int64_t id, const jvalue& value, bool object, JNIEnv* callerEnv);

} // namespace copper::bridge::bus::Waiters

#endif // COPPER_BRIDGE_BUS_INTERNAL_WAITERS_H
