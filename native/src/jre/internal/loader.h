#ifndef COPPER_BRIDGE_JRE_INTERNAL_LOADER_H
#define COPPER_BRIDGE_JRE_INTERNAL_LOADER_H

#include <string>

namespace copper::bridge::jre::Loader {

    // What this module's own files share about the libraries it loaded; no generated table takes its address.

    // One of the JRE's libraries, by its path relative to lib/: the handle back, or nullptr when it was not loaded.
    void* GetJreLibrary(const std::string& relativePath);

} // namespace copper::bridge::jre::Loader

#endif // COPPER_BRIDGE_JRE_INTERNAL_LOADER_H
