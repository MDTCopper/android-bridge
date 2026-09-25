#ifndef COPPER_BRIDGE_BUS_INTERNAL_HANDLERS_H
#define COPPER_BRIDGE_BUS_INTERNAL_HANDLERS_H

#include "gen/bus.h"

#include <jni.h>

namespace copper::bridge::bus::Handlers {

    // What performing a row asks of the handlers bound to a side: which instance and which method id serve
    // a kind. Nothing here knows a Java class name - the names and descriptors it resolves come out of the
    // generated rows - which is what lets a branch bind an interface the native side never heard of.

    // The instance that declares this kind, or nullptr.
    jobject BoundInstance(jni::Side side, gen::Bus::Kind kind);

    // The method id of this kind for that side, or nullptr.
    jmethodID MethodOf(jni::Side side, gen::Bus::Kind kind);

    // The class that declares a static row, resolved once and kept as a global reference.
    jclass ResolveOwner(jni::Side side, const gen::Bus::CallEntry& entry);

} // namespace copper::bridge::bus::Handlers

#endif // COPPER_BRIDGE_BUS_INTERNAL_HANDLERS_H
