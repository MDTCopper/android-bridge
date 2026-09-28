#ifndef COPPER_BRIDGE_JNI_H
#define COPPER_BRIDGE_JNI_H

#include <jni.h>

#include <string>

namespace copper::bridge::util::Jni {

    // The JNIEnv plumbing the binding layer needs, and nothing else: the one place under util/ that knows about a VM.
    // A pending exception surfaces much later at an unrelated call, so each helper decides whether one is meaningful.

    std::string ToString(JNIEnv* env, jstring value);

    void ClearException(JNIEnv* env);

    bool RegisterMethods(JNIEnv* env, const char* className, const JNINativeMethod* methods, int count);

} // namespace copper::bridge::util::Jni

#endif // COPPER_BRIDGE_JNI_H
