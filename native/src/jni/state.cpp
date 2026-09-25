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
            std::string cacheDir;
            std::string abi;
            int abiCode = -1;
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

    int Init(JNIEnv* env, jclass, jstring cacheDir, jstring abi) {
        values.cacheDir = util::Jni::ToString(env, cacheDir);
        values.abi = util::Jni::ToString(env, abi);
        values.abiCode = util::Abi::CodeFromName(values.abi);

        const int compiled = util::Abi::CompiledCode();
        util::Log::InfoF(util::Log::NO_TAG, "init: cache=%s abi=%s compiledAbiCode=%d", values.cacheDir.c_str(), values.abi.c_str(),
             compiled);

        return values.abiCode == compiled ? 0 : 1;
    }

} // namespace copper::bridge::jni::State
