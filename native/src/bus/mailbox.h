#ifndef COPPER_BRIDGE_BUS_MAILBOX_H
#define COPPER_BRIDGE_BUS_MAILBOX_H

#include "bus/message.h"

// One mailbox per side: where a call waits until that side pumps. It keeps messages in order, does not know what they
// mean, and never calls Java.

namespace copper::bridge::bus::Mailbox {

    void Enqueue(jni::Side to, Message&& message);

} // namespace copper::bridge::bus::Mailbox

#endif // COPPER_BRIDGE_BUS_MAILBOX_H
