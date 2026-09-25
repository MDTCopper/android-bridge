#include "jre/launcher.h"

#include "gen/binding.h"
#include "gen/vmcall.h"
#include "jre/internal/launcher.h"
#include "jre/internal/loader.h"
#include "util/jni.h"
#include "util/log.h"

#include <dlfcn.h>
#include <jni.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unistd.h>
#include <vector>

namespace copper::bridge::jre::Launcher {

    namespace Log = util::Log;

    namespace {

        // The signature JLI_Launch has: the JDK headers that declare it are not part of the NDK.
        using JliLaunch = jint (*)(int argc, char** argv, int jargc, const char** jargv, int appclassc,
                           const char** appclassv, const char* fullVersion, const char* dotVersion,
                           const char* pname, const char* lname, jboolean javaargs,
                           jboolean cpwildcard, jboolean javaw, jint ergo);

        // Only used for JLI_Launch's own banner and a couple of internal comparisons; the game never sees
        // them.
        constexpr const char* FULL_VERSION = "1.8.0-internal";
        constexpr const char* DOT_VERSION = "1.8";

        /** Hands the ending to ART, which owns the screen. @return whether it was handed over. */
        bool RequestArtEnd() {
            switch (gen::VmCall::EndAfterJvmExit()) {
            case gen::Binding::Outcome::Done:
                return true;
            case gen::Binding::Outcome::NotResolved:
                Log::Warn("LAUNCHER", "the ART ending entry is not resolved, so the screen is not asked to leave");
                return false;
            case gen::Binding::Outcome::Failed:
                Log::Warn("LAUNCHER", "cannot reach ART from this thread, so the screen is not asked to leave");
                return false;
            }
            return false;
        }

    } // namespace

    /**
     * Ends what the VM left behind, in the way its exit code asks for.
     *
     * <p>A non-zero status means the VM could not do its job: {@code _exit} runs no teardown at all, because
     * that teardown is what takes ART down with it - an ART-side thread that touches the destroyed state
     * dies of FORTIFY and SIGABRT (measured on the tablet, on the OEM insets thread).</p>
     *
     * <p>A clean return is the game ending on its own terms: the screen leaves with its own transition and
     * this thread parks, so that teardown never runs; the host kills what is left before the next game.</p>
     */
    [[noreturn]] void EndProcessNow(int status) {
        if (status != 0) {
            Log::InfoF("LAUNCHER", "the JVM exited with %d, exiting", status);
            _exit(status);
        }

        Log::Info("LAUNCHER", "the JVM is gone, leaving this screen");
        fflush(nullptr);
        RequestArtEnd();

        for (;;)
            pause();
    }

    int LaunchJvm(JNIEnv* env, jclass, jobjectArray javaArgv) {
        if (javaArgv == nullptr) {
            Log::Error("LAUNCHER", "launchJVM called without arguments");
            return -1;
        }

        const jsize count = env->GetArrayLength(javaArgv);
        if (count <= 0) {
            Log::Error("LAUNCHER", "launchJvm called with an empty argument list");
            return -1;
        }

        std::vector<std::string> args;
        args.reserve(static_cast<size_t>(count));
        for (jsize i = 0; i < count; i++) {
            auto element = static_cast<jstring>(env->GetObjectArrayElement(javaArgv, i));
            args.push_back(util::Jni::ToString(env, element));
            if (element != nullptr)
                env->DeleteLocalRef(element);
        }

        // The VM keeps these strings for the rest of the process, so they are copied into memory that
        // outlives this call.
        std::vector<char*> argv;
        argv.reserve(args.size());
        for (const std::string& arg : args) {
            char* copy = static_cast<char*>(malloc(arg.size() + 1));
            if (copy == nullptr) {
                Log::Error("LAUNCHER", "out of memory while copying the JVM arguments");
                for (char* existing : argv)
                    free(existing);
                return -1;
            }
            memcpy(copy, arg.c_str(), arg.size() + 1);
            argv.push_back(copy);
        }

        // The loader opened libjli when it loaded the JRE: ask it for that library rather than open the file
        // a second time.
        void* libjli = Loader::GetJreLibrary("libjli.so");
        if (libjli == nullptr) {
            Log::Error("LAUNCHER", "the JRE's launcher library is not loaded, so there is nothing to start");
            return -1;
        }

        auto launch = reinterpret_cast<JliLaunch>(dlsym(libjli, "JLI_Launch"));
        if (launch == nullptr) {
            Log::Error("LAUNCHER", "libjli.so does not export JLI_Launch");
            return -1;
        }


        Log::InfoF("LAUNCHER", "calling JLI_Launch with %d arguments", static_cast<int>(argv.size()));
        jint result = launch(static_cast<int>(argv.size()), argv.data(), 0, nullptr, 0, nullptr,
                     FULL_VERSION, DOT_VERSION, argv[0], argv[0], JNI_FALSE, JNI_TRUE,
                     JNI_FALSE, 0);

        // What the VM printed is already in the log - the writes were taken over at the calls that made them
        // - so there is nothing left in a pipe to wait for.
        Log::InfoF("LAUNCHER", "JLI_Launch returned %d", static_cast<int>(result));

        // The same ending the other path gets, so both leave the same way: the screen goes, and then the
        // process ends with the VM's status. Its teardown is safe here - the VM that shared this process's
        // C++ state is already gone - and the process must not outlive it.
        EndProcessNow(static_cast<int>(result));
    }

} // namespace copper::bridge::jre::Launcher
