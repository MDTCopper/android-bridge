#ifndef COPPER_BRIDGE_JNI_LOG_H
#define COPPER_BRIDGE_JNI_LOG_H

#include <jni.h>

namespace copper::bridge::jni::Log {

    // The log's Java face: a line reaches the log as plain strings, and turning a Java string into one is what this
    // file is for.

    /** One line Java handed over: its level, its side's logcat tag, and the text the file holds. */
    void LogLine(JNIEnv* env, jclass, jint level, jstring logcatTag, jstring line);

} // namespace copper::bridge::jni::Log

#endif // COPPER_BRIDGE_JNI_LOG_H
