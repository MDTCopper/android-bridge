#ifndef COPPER_BRIDGE_BUS_DISPATCH_H
#define COPPER_BRIDGE_BUS_DISPATCH_H

#include "gen/bus.h"

#include <jni.h>

// Perform: one row straight away, or everything a side has queued.
//
// A direct row runs on the calling thread with nothing queued and nothing woken - that is what a caller
// asks for. The two pumps are the entry points the generated tables register: the game loop calls the
// first every frame, and the ART side's message loop calls the second.
//
// A posted row is taken off the queue and performed on the thread that pumps, and the mailbox lock is
// never held while a handler runs: it may take its time, and a thread that queues another message
// meanwhile must not be made to wait for it.

namespace copper::bridge::bus::Dispatch {

    /** Performs one direct row on the calling thread: rebuilds the arguments in the callee's VM, calls
     *  the bound handler, and rebuilds the result in the caller's VM. Nothing is queued. */
    void InvokeDirect(JNIEnv* env, gen::Bus::Kind kind, const jvalue* args, int count, jvalue* out);

    /** Performs every call queued for either side, on this thread. The game loop's entry point.
     *
     *  <p>Both mailboxes, because this runs on the one thread that is turning anyway.</p> */
    void Pump(JNIEnv* env, jclass);

    /** Performs every call queued for ART, on this thread. The ART message loop's entry point. */
    void Drain(JNIEnv* env, jclass);

} // namespace copper::bridge::bus::Dispatch

#endif // COPPER_BRIDGE_BUS_DISPATCH_H
