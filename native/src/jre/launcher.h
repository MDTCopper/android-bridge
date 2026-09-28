#ifndef COPPER_BRIDGE_JRE_LAUNCHER_H
#define COPPER_BRIDGE_JRE_LAUNCHER_H

#include <jni.h>

namespace copper::bridge::jre::Launcher {

    // Starts the JVM that runs the game, inside this process: JLI_Launch does not return until the game exits, so the
    // caller owns a dedicated thread.

    // Calls JLI_Launch; returns the VM's exit code, or -1 when the call is unusable.
    int LaunchJvm(JNIEnv* env, jclass, jobjectArray javaArgv);

} // namespace copper::bridge::jre::Launcher

#endif // COPPER_BRIDGE_JRE_LAUNCHER_H
