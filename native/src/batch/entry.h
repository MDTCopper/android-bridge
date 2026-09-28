#ifndef COPPER_BRIDGE_BATCH_ENTRY_H
#define COPPER_BRIDGE_BATCH_ENTRY_H

#include <jni.h>

namespace copper::bridge::batch::Entry {

    // The two operations every channel shares, as the VM sees them. They only move bytes: which channels exist is
    // generated, and one pair serves every channel.

    /** Queues one frame into a channel. Nothing to queue, or a channel that does not exist, is a no-op. */
    void Push(JNIEnv* env, jclass, jint channel, jbyteArray buffer, jint length);

    jbyteArray Poll(JNIEnv* env, jclass, jint channel, jbyteArray reuse);

} // namespace copper::bridge::batch::Entry

#endif // COPPER_BRIDGE_BATCH_ENTRY_H
