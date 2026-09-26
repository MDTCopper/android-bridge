#ifndef COPPER_BRIDGE_JRE_LOADER_H
#define COPPER_BRIDGE_JRE_LOADER_H

#include <jni.h>

namespace copper::bridge::jre::Loader {

    // Turns a JRE directory into loaded libraries.
    //
    // The two entry points are libjli (JLI_Launch) and libjvm (JNI_CreateJavaVM). On top of their DT_NEEDED
    // closure, every library shipped next to them - <jre>/lib and <jre>/lib/<arch> - is opened too: the Java
    // side asks for some of those by name long after the launch (libnet.so, libzip.so, libmanagement.so),
    // and by then the bionic linker cannot resolve a name that is not already in memory. The price is one
    // skip line per library whose dependencies this JRE does not ship.
    //
    // The hooks go on over those libraries once they are all in (jre/hook.h); only a missing entry point is fatal.

    // Resolves and loads the JRE under the directory the caller named, in dependency order. The directory is
    // copied out of the Java string rather than held as a view over it: the loading is long enough that
    // pinning the VM's string for all of it would be the wrong trade. An empty name loads nothing; only a
    // missing entry point is fatal.
    void LoadJreLibraries(JNIEnv* env, jclass, jstring jreDir);

    // Adds a directory to the linker search path, through `android_update_LD_LIBRARY_PATH`. Only some ROMs
    // use it, so it is a best effort next to loading libraries by absolute path. Call it before the loads it
    // should cover. The ART side calls it for the JRE directories and for the staged arc natives, which the
    // game then asks for by name.
    void UpdateLinkerPath(JNIEnv* env, jclass, jstring path);

    // Sets a process environment variable, which the JVM that is started afterwards reads. The C string
    // setenv wants is built here, because a Java string is not one.
    void SetEnv(JNIEnv* env, jclass, jstring key, jstring value);

} // namespace copper::bridge::jre::Loader

#endif // COPPER_BRIDGE_JRE_LOADER_H
