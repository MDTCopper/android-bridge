#ifndef COPPER_BRIDGE_BUS_INTERNAL_MAILBOX_H
#define COPPER_BRIDGE_BUS_INTERNAL_MAILBOX_H

#include "bus/mailbox.h"

// The draining end of a mailbox. Sending is public - a caller queues a message and the side that owns the
// mailbox is woken; taking one out is the pump's and giving them all back is the stop's, so these stay
// inside the bus.

namespace copper::bridge::bus::Mailbox {

    /** Takes one message addressed to `me`; returns the null kind when that mailbox is empty. */
    gen::Bus::Kind Take(jni::Side me, Message& out);

    /** Marks the mailbox as being drained, before the first message is taken out of it. */
    void BeginDrain(jni::Side me);

    /** Gives back every message still queued for `side`, because nothing will perform them any more, and
     *  returns how many there were. */
    int Clear(jni::Side side);

} // namespace copper::bridge::bus::Mailbox

#endif // COPPER_BRIDGE_BUS_INTERNAL_MAILBOX_H
