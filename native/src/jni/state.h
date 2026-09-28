#ifndef COPPER_BRIDGE_STATE_H
#define COPPER_BRIDGE_STATE_H

#include <jni.h>

namespace copper::bridge::jni::State {

    // Process wide state: which VM is which. Both are kept because the bus serves both directions.

    // The VM that loaded the library first, which is always ART: recorded on the first load, never replaced.
    JavaVM* ArtVm();
    void SetArtVm(JavaVM* vm);

    // The VM JLI_Launch created, recorded by its own load of this library; never recorded, answers are dropped.
    JavaVM* Jvm();
    void SetJvm(JavaVM* vm);

} // namespace copper::bridge::jni::State

#endif // COPPER_BRIDGE_STATE_H
