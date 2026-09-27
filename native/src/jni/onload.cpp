#include "gen/binding.h"

#include "jni/internal/log.h"
#include "jni/state.h"
#include "util/log.h"

namespace copper::bridge::jni {

    // The one entry point the VM looks up by name.
    //
    // JNI_OnLoad runs twice per process: once when ART loads the library, and once when the JVM that
    // JLI_Launch created calls System.load on the same file. A native method is bound to the VM whose
    // JNI_OnLoad registered it, so both loads register every table that resolves.
    //
    // Which VM a load belongs to cannot be told from the class names - the whole bridge jar is on both
    // classpaths - but only one has to be remembered: ART loads this library before a JVM exists. The
    // library is built with hidden visibility, so this entry point asks for the default explicitly, and
    // `extern "C"` is what makes it findable: C linkage is the symbol the VM looks for, namespace or not.

    extern "C" __attribute__((visibility("default")))
    JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
        JNIEnv* env = nullptr;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr)
            return JNI_ERR;

        // Which VM this load belongs to is decided before anything else, because a step below needs it: the log
        // is read from ART's Java, so ART's environment has to be answerable by the time the log file is opened.
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

        // The handles this library calls back through come next, while this thread is still a thread of the VM
        // that asked for the library: a class lookup uses the calling thread's class loader, and the bus later
        // wakes ART from the other VM's threads, where these classes cannot be seen at all.
        gen::Binding::ResolveReverse(env, State::ArtVm() == vm ? Side::Art : Side::Jvm);

        // The tables are registered before the log file is opened: nothing in them can disagree with the class
        // it was written from, and the one thing that could go wrong reports itself to Android's log.
        const int bound = gen::Binding::BindAll(env);

        // Then the log file, because everything below - including a failure - has to end up somewhere: the ART
        // side had it open until the moment before this load, and takes it back when this returns JNI_ERR.
        if (!Log::Setup())
            return JNI_ERR;

        // The native side's banner, in the same shape as the ART and JVM sides'. Only the ART load prints it,
        // so the JVM's later load of this file does not repeat it. It sits here because the log file has to be
        // open before anything can be written to it.
        if (loaded == 1)
            util::Log::Info(util::Log::NO_TAG, "CopperBridge (Native side)");

        if (bound == 0)
            util::Log::Warn(util::Log::NO_TAG, "no binding table resolved in this VM");

        // Said now rather than where it was decided: the log file was not open yet.
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
