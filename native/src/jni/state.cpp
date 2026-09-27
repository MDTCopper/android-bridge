#include "jni/state.h"

#include "util/abi.h"
#include "util/jni.h"
#include "util/log.h"

#include <string>

namespace copper::bridge::jni::State {

    namespace {

        // The values themselves, one object for the process. It needs nothing a load cannot give it, so it is
        // built when this library is loaded rather than by the first entry point that reaches for it; `Init`
        // fills in what Java declares at startup.
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
