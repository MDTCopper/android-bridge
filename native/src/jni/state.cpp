#include "jni/state.h"

namespace copper::bridge::jni::State {

    namespace {

        // The values themselves, one object for the process. It needs nothing a load cannot give it, so it is
        // built when this library is loaded rather than by the first entry point that reaches for it.
        struct Storage {
            JavaVM* artVm = nullptr;
            JavaVM* jvm = nullptr;
        };

        Storage values;

    } // namespace

    JavaVM* ArtVm() {
        return values.artVm;
    }

    void SetArtVm(JavaVM* vm) {
        values.artVm = vm;
    }

    JavaVM* Jvm() {
        return values.jvm;
    }

    void SetJvm(JavaVM* vm) {
        values.jvm = vm;
    }

} // namespace copper::bridge::jni::State
