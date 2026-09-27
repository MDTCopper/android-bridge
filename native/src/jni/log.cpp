#include "jni/log.h"

#include "gen/binding.h"
#include "gen/vmcall.h"
#include "jni/env.h"
#include "jni/internal/log.h"
#include "util/jni.h"
#include "util/log.h"

#include <string>

namespace copper::bridge::jni::Log {
    namespace Jni = util::Jni;
    namespace VmCall = gen::VmCall;
    using gen::Binding::Outcome;

    bool Setup() {
        // The path and the logcat flag are ART's Java's to answer - that side owned the file until this
        // library was loaded - so the whole of this runs in ART's environment.
        jni::Env art(jni::Side::Art);
        if (!art.Ok()) {
            util::Log::Error("LOG", "cannot reach ART to ask for the log file");
            return false;
        }

        jint level = util::Log::INFO;
        jstring path = nullptr;
        jboolean wanted = JNI_FALSE;

        if (VmCall::LogLevel(&level) != Outcome::Done)
            return false;

        // A call that did not reach the member is the same answer as a missing file: this side has nothing
        // to write through, and the caller in Java is the one that has to say what went wrong.
        if (VmCall::LogFilePath(&path) != Outcome::Done
                || VmCall::LogcatEnabled(&wanted) != Outcome::Done) {
            if (path != nullptr)
                art.Get()->DeleteLocalRef(path);
            return false;
        }

        const std::string filePath = Jni::ToString(art.Get(), path);
        if (path != nullptr)
            art.Get()->DeleteLocalRef(path);

        util::Log::SetLevel(static_cast<util::Log::Level>(level));
        return util::Log::OpenFile(filePath, wanted == JNI_TRUE);
    }

    void LogLine(JNIEnv* env, jclass, jint level, jstring logcatTag, jstring line) {
        // Reached by both VMs: a JVM has no android.util.Log at all. The line is the file's text already,
        // head included, and the tag names the side that produced it.
        const std::string tagText = Jni::ToString(env, logcatTag);
        util::Log::LogLine(static_cast<util::Log::Level>(level), tagText.c_str(), Jni::ToString(env, line));
    }

} // namespace copper::bridge::jni::Log
