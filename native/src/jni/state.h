#ifndef COPPER_BRIDGE_STATE_H
#define COPPER_BRIDGE_STATE_H

#include <jni.h>

namespace copper::bridge::jni::State {

    // Process wide state: which VM is which.
    //
    // Two VMs share this library, so an entry point has to know which one it is talking to; both are kept,
    // because the bus serves both directions. There is one of each per process, so the values live behind
    // functions rather than in an object that would only be something to hand around.

    // The VM that loaded the library first, which is always the ART side; recorded on the first load and
    // never replaced.
    JavaVM* ArtVm();
    void SetArtVm(JavaVM* vm);

    // The VM that JLI_Launch created, recorded by its own load of this library. A process that never recorded
    // it would drop every answer silently - the pump resolves the delivery method through this side's own
    // environment - which is what the file chooser and text input callbacks need.
    JavaVM* Jvm();
    void SetJvm(JavaVM* vm);

} // namespace copper::bridge::jni::State

#endif // COPPER_BRIDGE_STATE_H
