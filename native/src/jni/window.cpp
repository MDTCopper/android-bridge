#include "jni/window.h"

#include "util/log.h"

#include <android/native_window.h>
#include <android/native_window_jni.h>

namespace copper::bridge::jni {

    namespace Log = util::Log;

    jlong NativeWindow(JNIEnv* env, jobject, jobject surface) {
        if (surface == nullptr) {
            Log::Error("JNI", "nativeWindow called without a surface");
            return 0;
        }

        ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
        if (window == nullptr)
            Log::Error("JNI", "ANativeWindow_fromSurface returned null");
        return reinterpret_cast<jlong>(window);
    }

} // namespace copper::bridge::jni
