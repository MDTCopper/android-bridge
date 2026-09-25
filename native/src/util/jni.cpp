#include "util/jni.h"

#include "util/log.h"

namespace copper::bridge::util::Jni {

    std::string ToString(JNIEnv* env, jstring value) {
        if (value == nullptr)
            return std::string();
        const char* chars = env->GetStringUTFChars(value, nullptr);
        if (chars == nullptr)
            return std::string();
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    }

    void ClearException(JNIEnv* env) {
        if (env->ExceptionCheck())
            env->ExceptionClear();
    }

    bool RegisterMethods(JNIEnv* env, const char* className, const JNINativeMethod* methods, int count) {
        jclass clazz = env->FindClass(className);
        if (clazz == nullptr) {
            // Expected for the class that belongs to the other VM: this VM's class loaders cannot see
            // it, and it is registered when that VM loads this library.
            env->ExceptionClear();
            return false;
        }

        jint result = env->RegisterNatives(clazz, methods, count);
        env->DeleteLocalRef(clazz);
        if (result != JNI_OK) {
            env->ExceptionClear();
            util::Log::ErrorF("JNI", "RegisterNatives failed for %s", className);
            return false;
        }

        util::Log::InfoF("JNI", "registered natives for %s", className);
        return true;
    }

    } // namespace copper::bridge