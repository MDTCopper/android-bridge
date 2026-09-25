#ifndef COPPER_BRIDGE_LINKER_H
#define COPPER_BRIDGE_LINKER_H

#include "util/elf.h"

#include <map>
#include <set>
#include <string>
#include <vector>

namespace copper::bridge::util {

    // A dlopen that works on an Android JRE.
    //
    // The Android linker cannot be pointed at a JRE directory: it ignores LD_LIBRARY_PATH, and the
    // libraries a JRE ships usually carry no usable DT_RUNPATH, so opening one by soname fails even when
    // the file sits in the same directory as its caller. This loader reads each library's DT_NEEDED
    // closure with the ELF reader and opens every member by absolute path, dependencies first, so that a
    // later dlopen by name finds everything it needs already in memory.
    //
    // The shape mirrors dlopen on purpose: one object holds what a linker holds. A dependency that cannot
    // be found or opened is counted and logged, never fatal; only the library asked for can make Load fail.

    class Linker {
    public:
        // How a load went, for the one summary line the caller logs.
        struct Stats {
            int loaded = 0;
            int skipped = 0;
        };

        // `searchDirs` are tried last, after the caller's own DT_RUNPATH / DT_RPATH entries and the
        // caller's own directory.
        explicit Linker(std::vector<std::string> searchDirs);

        // Loads `path` and its dependency closure, dependencies first; nullptr when `path` itself could not
        // be loaded.
        void* Load(const std::string& path);

        Stats Statistics() const;

        // Every library this linker has opened, by absolute path - what it loaded, not what the process
        // happens to have loaded.
        std::vector<std::string> LoadedPaths() const;

    private:
        // Whether the library or one of its dependencies failed. Only the top level call turns that into
        // Load returning nullptr; below it, a failure is logged and stepped over.
        bool Visit(const std::string& path, int depth);

        // Walks one library's DT_NEEDED list; what could not be resolved is counted and logged.
        void VisitDependencies(const Elf::Deps& deps, int depth);

        // Candidate directories for one dependency: the caller's own search paths first, then the caller's
        // directory, then searchDirs. Order matters, because a JRE ships different builds of the same name
        // in different subdirectories.
        std::vector<std::string> CandidatesFor(const Elf::Deps& caller) const;

        // Finds a soname by looking only at the filesystem. Nothing is opened.
        std::string FindLibrary(const std::string& soname, const Elf::Deps& caller);

        std::vector<std::string> searchDirs;
        // Absolute path of a library already resolved, by the name it was asked for and by its soname, so
        // a diamond dependency opens once.
        std::map<std::string, std::string> knownPaths;
        // Every absolute path already handled.
        std::set<std::string> visited;
        // Paths currently on the recursion stack, to break cycles.
        std::set<std::string> visiting;
        // Handles of the libraries opened so far, so Load can answer like dlopen does.
        std::map<std::string, void*> handles;
        Stats counts;
    };

} // namespace copper::bridge::util

#endif // COPPER_BRIDGE_LINKER_H
