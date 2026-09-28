#ifndef COPPER_BRIDGE_ELF_H
#define COPPER_BRIDGE_ELF_H

#include <string>
#include <vector>

namespace copper::bridge::util::Elf {

    // Minimal ELF reader, used to work out which shared libraries a JRE needs before they are loaded, because the
    // Android linker cannot be pointed at the JRE directory. Only the dynamic section, its string table, DT_NEEDED,
    // DT_SONAME, DT_RUNPATH and DT_RPATH are implemented - no symbol resolution, no relocations.

    struct Deps {
        std::string path;
        // DT_SONAME, or an empty string when the library does not declare one.
        std::string soname;
        // Every DT_NEEDED entry, in the order the linker would see them.
        std::vector<std::string> needed;
        // DT_RUNPATH plus DT_RPATH entries, split on ':' with $ORIGIN left unexpanded.
        std::vector<std::string> searchPaths;
    };

    bool ReadDeps(const std::string& path, Deps& out, std::string& error);

    std::string ExpandOrigin(const std::string& entry, const std::string& origin);

} // namespace copper::bridge::util::Elf

#endif // COPPER_BRIDGE_ELF_H
