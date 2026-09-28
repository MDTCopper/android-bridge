#include "gen/binding.h"

#include "jni/internal/log.h"
#include "jni/state.h"
#include "util/log.h"

namespace copper::bridge::jni {

    // The one entry point the VM looks up by name. JNI_OnLoad runs twice per process: when ART loads the library and
    // when the JVM calls System.load on the same file. A native method binds to the VM whose JNI_OnLoad registered it,
    // so both loads register every table that resolves; ART's load is the one that comes first.

    extern "C" __attribute__((visibility("default")))
    JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
        JNIEnv* env = nullptr;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr)
            return JNI_ERR;

        // Decided before anything else: the log is read from ART's Java, so ART's environment has to answer first.
        int loaded = 0;
        if (State::ArtVm() == nullptr) {
            State::SetArtVm(vm);
            loaded = 1;
        } else if (State::Jvm() == nullptr && State::ArtVm() != vm) {
            if (State::ArtVm() != vm) {
                State::SetJvm(vm);
                loaded = 2;
            } else {
                loaded = 3;
            }
        } else {
            loaded = 4;
        }

        // The handles this library calls back through come next, while this thread still belongs to the asking VM.
        gen::Binding::ResolveReverse(env, State::ArtVm() == vm ? Side::Art : Side::Jvm);

        const int bound = gen::Binding::BindAll(env);

        // Then the log, because everything below - including a failure - has to end up somewhere.
        if (!Log::Setup())
            return JNI_ERR;

        if (loaded == 1)
            util::Log::Info(util::Log::NO_TAG, "CopperBridge (Native side)");

        if (bound == 0)
            util::Log::Warn(util::Log::NO_TAG, "no binding table resolved in this VM");

        if (loaded == 1)
            util::Log::Info(util::Log::NO_TAG, "loaded by the ART VM");
        else if (loaded == 2)
            util::Log::Info(util::Log::NO_TAG, "loaded by the JVM");
        else if (loaded == 3)
            util::Log::Warn(util::Log::NO_TAG, "reloaded by the ART VM");
        else if (loaded == 4)
          util::Log::Warn(util::Log::NO_TAG, "reloaded by the JVM");

        return JNI_VERSION_1_6;
    }

} // namespace copper::bridge::jni
