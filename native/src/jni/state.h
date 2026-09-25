#ifndef COPPER_BRIDGE_STATE_H
#define COPPER_BRIDGE_STATE_H

#include <jni.h>

#include <string>

namespace copper::bridge::jni::State {

    // Process wide state: which VM is which, and what the Java side asked for at startup.
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

    /**
     * Records what the Java side declared about this process, and reports whether the library that is
     * already loaded was built for it.
     *
     * A mismatch means the wrong libcopperbridge.so was extracted for this device, which the caller wants as
     * a value rather than as a crash in the middle of a load.
     *
     * @return 0 when the loaded library matches the declared ABI, 1 when it does not
     */
    int Init(JNIEnv* env, jclass, jstring cacheDir, jstring abi);

} // namespace copper::bridge::jni::State

#endif // COPPER_BRIDGE_STATE_H
