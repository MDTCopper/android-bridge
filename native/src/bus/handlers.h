#ifndef COPPER_BRIDGE_BUS_HANDLERS_H
#define COPPER_BRIDGE_BUS_HANDLERS_H

#include <jni.h>

namespace copper::bridge::bus::Handlers {

    // Binding, as the VM sees it.
    //
    // One entry per side, because the caller already said which one it is: `ArtBus.bind` hands over the ART
    // half and `JvmBus.bind` the JVM half. The object's own type chain decides which rows of that side it
    // serves; a type that serves none of them is not a mistake, and the line that says so is verbose.

    // Registers one handler instance against the ART side's rows. One instance per declaring class.
    void BindArt(JNIEnv* env, jclass, jobject handlers);

    // The same for the JVM side.
    void BindJvm(JNIEnv* env, jclass, jobject handlers);

} // namespace copper::bridge::bus::Handlers

#endif // COPPER_BRIDGE_BUS_HANDLERS_H
