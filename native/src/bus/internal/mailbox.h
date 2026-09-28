#ifndef COPPER_BRIDGE_BUS_INTERNAL_MAILBOX_H
#define COPPER_BRIDGE_BUS_INTERNAL_MAILBOX_H

#include "bus/mailbox.h"

// The draining end of a mailbox: sending is public, taking one out is the pump's, giving them all back the stop's.

namespace copper::bridge::bus::Mailbox {

    /** Takes one message addressed to `me`; returns the null kind when that mailbox is empty. */
    gen::Bus::Kind Take(jni::Side me, Message& out);

    void BeginDrain(jni::Side me);

    /** Gives back every message still queued for `side`, nothing performing them any more, and returns the count. */
    int Clear(jni::Side side);

} // namespace copper::bridge::bus::Mailbox

#endif // COPPER_BRIDGE_BUS_INTERNAL_MAILBOX_H
