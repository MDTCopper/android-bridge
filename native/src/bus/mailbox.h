#ifndef COPPER_BRIDGE_BUS_MAILBOX_H
#define COPPER_BRIDGE_BUS_MAILBOX_H

#include "bus/message.h"

// One mailbox per side: where a call waits until that side pumps.
//
// The mailbox is the transport and nothing more. It takes a message, keeps it in order, and hands it back
// to whoever drains it; it does not know what a message means, and it never calls Java.

namespace copper::bridge::bus::Mailbox {

    /** Queues one message for the side that will perform it, and wakes that side when it needs waking. */
    void Enqueue(jni::Side to, Message&& message);

} // namespace copper::bridge::bus::Mailbox

#endif // COPPER_BRIDGE_BUS_MAILBOX_H
