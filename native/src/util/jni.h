#ifndef COPPER_BRIDGE_JNI_H
#define COPPER_BRIDGE_JNI_H

#include <jni.h>

#include <string>

namespace copper::bridge::util::Jni {

    // The JNIEnv plumbing the binding layer needs, and nothing else. This is the one place under util/ that
    // knows about a VM: a file that has to ask Java for something asks here and hands the answer on.
    //
    // Every call into Java can leave an exception pending, and a pending exception surfaces much later at an
    // unrelated call, so the helpers here decide per call whether an exception is meaningful.

    // Reads a Java string; a null string reads as an empty one, which is what every caller wants.
    std::string ToString(JNIEnv* env, jstring value);

    // Drops a pending Java exception. The game loop must never be interrupted by a failed clipboard read
    // or a missing window, and an uncleared exception would surface later at an unrelated call.
    void ClearException(JNIEnv* env);

    // Binds one VM's method table to a class. False when the class is not visible to this VM, which is the
    // normal case rather than an error: the ART class and the JVM class live in different VMs, so exactly one
    // of the two tables resolves on each load, and that is what tells the two loads apart.
    bool RegisterMethods(JNIEnv* env, const char* className, const JNINativeMethod* methods, int count);

} // namespace copper::bridge::util::Jni

#endif // COPPER_BRIDGE_JNI_H
