#ifndef COPPER_BRIDGE_ELF_H
#define COPPER_BRIDGE_ELF_H

#include <string>
#include <vector>

namespace copper::bridge::util::Elf {

    // Minimal ELF reader, used to work out which shared libraries a JRE needs before they are loaded.
    //
    // This exists because the Android linker cannot be pointed at the JRE directory: it ignores
    // LD_LIBRARY_PATH, and the libraries an Android JRE ships usually carry no usable DT_RUNPATH, so a
    // plain dlopen by name fails even when the file sits in the same directory as its caller.
    //
    // Only what is needed is implemented: the dynamic section, its string table, DT_NEEDED, DT_SONAME,
    // DT_RUNPATH and DT_RPATH. No symbol resolution, no relocations.

    // What one library says about itself.
    struct Deps {
        // The path this was read from, kept for diagnostics.
        std::string path;
        // DT_SONAME, or an empty string when the library does not declare one.
        std::string soname;
        // Every DT_NEEDED entry, in the order the linker would see them.
        std::vector<std::string> needed;
        // DT_RUNPATH plus DT_RPATH entries, already split on ':' with $ORIGIN left unexpanded.
        std::vector<std::string> searchPaths;
    };

    // Reads the dependencies of one ELF64 shared object. Returns false and fills `error` when the file
    // cannot be read or is not a 64 bit little endian ELF with a dynamic section; a library without
    // DT_NEEDED is a success with an empty list.
    bool ReadDeps(const std::string& path, Deps& out, std::string& error);

    // Expands $ORIGIN and ${ORIGIN} in a search path entry against the directory of `origin`.
    std::string ExpandOrigin(const std::string& entry, const std::string& origin);

} // namespace copper::bridge::util::Elf

#endif // COPPER_BRIDGE_ELF_H
