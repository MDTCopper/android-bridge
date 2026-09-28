#ifndef COPPER_BRIDGE_JNI_INTERNAL_LOG_H
#define COPPER_BRIDGE_JNI_INTERNAL_LOG_H

namespace copper::bridge::jni::Log {

    // The log's one Java lookup: log.cpp performs it, JNI_OnLoad asks for its answer before the VM can report.

    bool Setup();

} // namespace copper::bridge::jni::Log

#endif // COPPER_BRIDGE_JNI_INTERNAL_LOG_H
