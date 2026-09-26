#include "util/linker.h"

#include "util/elf.h"
#include "util/file.h"
#include "util/log.h"

#include <dlfcn.h>
#include <sys/stat.h>

#include <utility>

namespace copper::bridge::util {

    Linker::Linker(std::vector<std::string> dirs) : searchDirs(std::move(dirs)) {
    }

    Linker::Stats Linker::Statistics() const {
        return counts;
    }

    std::vector<std::string> Linker::CandidatesFor(const Elf::Deps& caller) const {
        std::vector<std::string> candidates;
        const std::string origin = util::File::DirName(caller.path);

        for (const std::string& entry : caller.searchPaths) {
            std::string expanded = Elf::ExpandOrigin(entry, origin);
            if (!expanded.empty())
                candidates.push_back(expanded);
        }
        // The caller's own directory is what makes the AWT style case work: a sibling library with no
        // usable runpath is still found, because this loader looks where the linker would not.
        candidates.push_back(origin);
        for (const std::string& dir : searchDirs)
            candidates.push_back(dir);

        return candidates;
    }

    std::string Linker::FindLibrary(const std::string& soname, const Elf::Deps& caller) {
        auto known = knownPaths.find(soname);
        if (known != knownPaths.end())
            return known->second;

        if (soname.find('/') != std::string::npos) {
            // DT_NEEDED containing a slash is a path relative to the caller, already usable as is.
            std::string path = soname[0] == '/' ? soname : util::File::DirName(caller.path) + "/" + soname;
            if (util::File::IsRegular(path)) {
                knownPaths[soname] = path;
                return path;
            }
            return std::string();
        }

        for (const std::string& dir : CandidatesFor(caller)) {
            std::string candidate = dir + "/" + soname;
            if (util::File::IsRegular(candidate)) {
                knownPaths[soname] = candidate;
                return candidate;
            }
        }
        return std::string();
    }

    void Linker::VisitDependencies(const Elf::Deps& deps, int depth) {
        for (const std::string& soname : deps.needed) {
            std::string dependency = FindLibrary(soname, deps);
            if (dependency.empty()) {
                // Not in this JRE, which for libc, libdl, libz and the rest of the platform's libraries is
                // the normal case: they are already mapped in, and asking the linker is what tells those
                // apart from a library that really is missing.
                chmod(soname.c_str(), 0500);
                if (dlopen(soname.c_str(), RTLD_LAZY) != nullptr)
                    continue;

                util::Log::InfoF("LINKER", "%*sskip %s: not found", depth * 2, "", soname.c_str());
                counts.skipped++;
                continue;
            }
            Visit(dependency, depth + 1);
        }
    }

    bool Linker::Visit(const std::string& path, int depth) {
        if (visited.count(path) > 0)
            return true;
        if (visiting.count(path) > 0) {
            // A cycle in DT_NEEDED is legal; the linker breaks it at the first repeating edge, and so does
            // this.
            util::Log::InfoF("LINKER", "%*scycle back to %s, ignored", depth * 2, "", path.c_str());
            return true;
        }

        visiting.insert(path);

        Elf::Deps deps;
        std::string error;
        if (!Elf::ReadDeps(path, deps, error)) {
            util::Log::InfoF("LINKER", "%*scannot read %s: %s", depth * 2, "", path.c_str(), error.c_str());
            visiting.erase(path);
            visited.insert(path);
            counts.skipped++;
            return false;
        }

        VisitDependencies(deps, depth);

        // Dependencies are opened first: an entry has to be present before the library needing it is
        // opened, otherwise the linker would go looking for it on its own and fail.
        chmod(path.c_str(), 0500);
        void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (handle == nullptr) {
            const char* reason = dlerror();
            util::Log::InfoF("LINKER", "%*sfailed %s: %s", depth * 2, "", path.c_str(), reason == nullptr ? "unknown" : reason);
            visiting.erase(path);
            // Recorded as visited anyway: retrying the same failing library once per dependent would
            // only multiply the same message.
            visited.insert(path);
            counts.skipped++;
            return false;
        }

        // The soname now maps to this path for every later lookup, so a second copy is never loaded under
        // another name.
        if (!deps.soname.empty())
            knownPaths[deps.soname] = path;

        handles[path] = handle;
        visiting.erase(path);
        visited.insert(path);
        counts.loaded++;
        util::Log::InfoF("LINKER", "%*sloaded %s", depth * 2, "", path.c_str());
        return true;
    }

    void* Linker::Load(const std::string& path) {
        if (!Visit(path, 0))
            return nullptr;
        auto found = handles.find(path);
        return found == handles.end() ? nullptr : found->second;
    }

    std::vector<std::string> Linker::LoadedPaths() const {
        std::vector<std::string> paths;
        paths.reserve(handles.size());
        for (const auto& entry : handles)
            paths.push_back(entry.first);
        return paths;
    }

} // namespace copper::bridge