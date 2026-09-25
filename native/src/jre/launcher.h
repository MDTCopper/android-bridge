#ifndef COPPER_BRIDGE_JRE_LAUNCHER_H
#define COPPER_BRIDGE_JRE_LAUNCHER_H

#include <jni.h>

namespace copper::bridge::jre::Launcher {

    // Starts the JVM that runs the game, inside this process.
    //
    // JLI_Launch does not return until the game exits, so the caller has to own a dedicated thread; the ART
    // main thread stays free to run its message loop. Everything below the entry is plain values - the
    // argument list - so this module knows nothing about JNI or about the Java classes that produced them.
    //
    // What the VM prints is taken over at the calls that write to its streams (jre/hook.cpp): an app process
    // gives it /dev/null for both, so its complaints would otherwise be thrown away.

    // Calls JLI_Launch with the arguments Java passed, asking the loader for the library it starts from.
    //
    // The strings are copied into memory that outlives the call, because the VM keeps them for its whole
    // lifetime. Returns the JVM exit code, or -1 when the library, its entry point or the argument list is
    // unusable: the success path never returns at all (see EndProcessNow).
    int LaunchJvm(JNIEnv* env, jclass, jobjectArray javaArgv);

} // namespace copper::bridge::jre::Launcher

#endif // COPPER_BRIDGE_JRE_LAUNCHER_H
