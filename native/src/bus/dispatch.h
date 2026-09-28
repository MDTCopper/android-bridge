#ifndef COPPER_BRIDGE_BUS_DISPATCH_H
#define COPPER_BRIDGE_BUS_DISPATCH_H

#include "gen/bus.h"

#include <jni.h>

// Perform: one row straight away, or everything a side has queued. A direct row runs on the calling thread, a posted
// row on the thread that pumps; the mailbox lock is never held while a handler runs.

namespace copper::bridge::bus::Dispatch {

    /** Performs one direct row on the calling thread: arguments rebuilt in the callee's VM, result in the caller's. */
    void InvokeDirect(JNIEnv* env, gen::Bus::Kind kind, const jvalue* args, int count, jvalue* out);

    /** Performs every call queued for either side, on this thread - the game loop's entry point. */
    void Pump(JNIEnv* env, jclass);

    /** Performs every call queued for ART, on this thread. The ART message loop's entry point. */
    void Drain(JNIEnv* env, jclass);

} // namespace copper::bridge::bus::Dispatch

#endif // COPPER_BRIDGE_BUS_DISPATCH_H
