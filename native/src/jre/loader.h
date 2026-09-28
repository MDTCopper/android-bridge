#ifndef COPPER_BRIDGE_JRE_LOADER_H
#define COPPER_BRIDGE_JRE_LOADER_H

#include <jni.h>

namespace copper::bridge::jre::Loader {

    // Turns a JRE directory into loaded libraries: libjli (JLI_Launch) and libjvm (JNI_CreateJavaVM), plus every
    // library shipped next to them, which the Java side later asks for by name. Only a missing entry point is fatal.

    void LoadJreLibraries(JNIEnv* env, jclass, jstring jreDir);

    // Adds a directory to the linker search path; `android_update_LD_LIBRARY_PATH` works on only some ROMs.
    void UpdateLinkerPath(JNIEnv* env, jclass, jstring path);

    void SetEnv(JNIEnv* env, jclass, jstring key, jstring value);

} // namespace copper::bridge::jre::Loader

#endif // COPPER_BRIDGE_JRE_LOADER_H
