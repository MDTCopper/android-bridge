#ifndef COPPER_BRIDGE_JRE_INTERNAL_LOADER_H
#define COPPER_BRIDGE_JRE_INTERNAL_LOADER_H

#include <string>

namespace copper::bridge::jre::Loader {

    // What this module's own files share about the libraries it loaded. Only the launcher calls it and no
    // generated table takes its address, so it stays out of the module's public face.

    // One of the JRE's libraries, by its path relative to the JRE's lib directory: "libjli.so",
    // "server/libjvm.so". Returns the handle the linker gave back, or nullptr when the JRE was not loaded
    // or that library was not part of it.
    void* GetJreLibrary(const std::string& relativePath);

} // namespace copper::bridge::jre::Loader

#endif // COPPER_BRIDGE_JRE_INTERNAL_LOADER_H
