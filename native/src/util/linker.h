#ifndef COPPER_BRIDGE_LINKER_H
#define COPPER_BRIDGE_LINKER_H

#include "util/elf.h"

#include <map>
#include <set>
#include <string>
#include <vector>

namespace copper::bridge::util {

    // A dlopen that works on an Android JRE: the linker cannot be pointed at a JRE directory, so this reads each
    // library's DT_NEEDED closure and opens every member by absolute path, dependencies first. A missing dependency
    // is logged, never fatal; only the library asked for can make Load fail.

    class Linker {
    public:
        struct Stats {
            int loaded = 0;
            int skipped = 0;
        };

        // `searchDirs` are tried last, after the caller's own DT_RUNPATH / DT_RPATH entries and its own directory.
        explicit Linker(std::vector<std::string> searchDirs);

        void* Load(const std::string& path);

        Stats Statistics() const;

        std::vector<std::string> LoadedPaths() const;

    private:
        bool Visit(const std::string& path, int depth);

        void VisitDependencies(const Elf::Deps& deps, int depth);

        // Candidate directories for one dependency: the caller's own search paths, then its directory, then searchDirs.
        std::vector<std::string> CandidatesFor(const Elf::Deps& caller) const;

        std::string FindLibrary(const std::string& soname, const Elf::Deps& caller);

        std::vector<std::string> searchDirs;
        std::map<std::string, std::string> knownPaths;
        std::set<std::string> visited;
        // Paths currently on the recursion stack, to break cycles.
        std::set<std::string> visiting;
        std::map<std::string, void*> handles;
        Stats counts;
    };

} // namespace copper::bridge::util

#endif // COPPER_BRIDGE_LINKER_H
