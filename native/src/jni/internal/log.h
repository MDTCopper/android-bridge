#ifndef COPPER_BRIDGE_JNI_INTERNAL_LOG_H
#define COPPER_BRIDGE_JNI_INTERNAL_LOG_H

namespace copper::bridge::jni::Log {

    // The log's one Java lookup, shared between this module's files: log.cpp performs it, and JNI_OnLoad
    // asks for its answer.

    /**
     * Asks Java where the log file is, whether Android's log is wanted, and at which level to write, and hands
     * all three to the log. This is the one lookup that has to happen before the VM the library is being
     * loaded into can report anything: a failure here is why JNI_OnLoad answers JNI_ERR.
     *
     * It takes no environment because all three answers come from ART's Java, and the call reaches ART's
     * environment itself. Returns false when the file itself is missing, which is also what an unanswered
     * call answers.
     */
    bool Setup();

} // namespace copper::bridge::jni::Log

#endif // COPPER_BRIDGE_JNI_INTERNAL_LOG_H
