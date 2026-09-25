#ifndef COPPER_BRIDGE_BATCH_ENTRY_H
#define COPPER_BRIDGE_BATCH_ENTRY_H

#include <jni.h>

namespace copper::bridge::batch::Entry {

    // The two operations every channel shares, as the VM sees them.
    //
    // They only move bytes: which channels exist and what they may hold is generated, and the codec that
    // produced the bytes is generated Java. One pair serves every channel, so adding a channel in either
    // direction changes nothing here.

    /** Queues one frame into a channel. Nothing to queue, or a channel that does not exist, is a no-op. */
    void Push(JNIEnv* env, jclass, jint channel, jbyteArray buffer, jint length);

    /** Takes every frame waiting on a channel in one crossing, reusing the caller's array when it is large
     *  enough and handing a right sized one back otherwise. */
    jbyteArray Poll(JNIEnv* env, jclass, jint channel, jbyteArray reuse);

} // namespace copper::bridge::batch::Entry

#endif // COPPER_BRIDGE_BATCH_ENTRY_H
