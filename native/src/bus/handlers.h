#ifndef COPPER_BRIDGE_BUS_HANDLERS_H
#define COPPER_BRIDGE_BUS_HANDLERS_H

#include <jni.h>

namespace copper::bridge::bus::Handlers {

    // Binding, as the VM sees it: one entry per side. A type that serves no row of that side is not a mistake.

    // Registers one handler instance against the ART side's rows. One instance per declaring class.
    void BindArt(JNIEnv* env, jclass, jobject handlers);

    void BindJvm(JNIEnv* env, jclass, jobject handlers);

} // namespace copper::bridge::bus::Handlers

#endif // COPPER_BRIDGE_BUS_HANDLERS_H
