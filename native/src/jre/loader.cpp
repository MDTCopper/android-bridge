#include "jre/loader.h"

#include "jre/hook.h"
#include "jre/internal/loader.h"
#include "util/file.h"
#include "util/jni.h"
#include "util/linker.h"
#include "util/log.h"

#include <cstdlib>
#include <dirent.h>
#include <dlfcn.h>

#include <algorithm>
#include <map>
#include <string>
#include <vector>

namespace copper::bridge::jre::Loader {

    namespace Log = util::Log;

    namespace {

        // The JRE's libraries by their path relative to the JRE's lib directory, each with the handle the
        // linker gave back. Starts empty: the JRE directory is a value only Java has at startup, so
        // LoadJreLibraries fills this in, not the first caller that asks for a library.
        std::map<std::string, void*> jreLibraries;

    } // namespace

    void* GetJreLibrary(const std::string& relativePath) {
        const auto found = jreLibraries.find(relativePath);
        return found == jreLibraries.end() ? nullptr : found->second;
    }

    namespace {

        // The architecture directory names a JRE may use. The exact spelling differs between distributions,
        // so several are tried.
        std::vector<std::string> ArchitectureNames() {
            std::vector<std::string> names;

#if defined(__aarch64__)
            names.push_back("aarch64");
            names.push_back("arm64");
#elif defined(__arm__)
            names.push_back("arm");
            names.push_back("armv7l");
#elif defined(__x86_64__)
            names.push_back("x86_64");
            names.push_back("amd64");
#elif defined(__i386__)
            names.push_back("i386");
            names.push_back("i486");
            names.push_back("i586");
#endif

            return names;
        }

        // Where a JRE keeps its libraries, in the order the entry points are most likely to be.
        std::vector<std::string> LayoutDirs(const std::string& jreDir) {
            std::vector<std::string> dirs;
            const std::string lib = jreDir + "/lib";

            for (const std::string& arch : ArchitectureNames()) {
                dirs.push_back(lib + "/" + arch + "/server");
                dirs.push_back(lib + "/" + arch + "/client");
            }
            dirs.push_back(lib + "/server");
            dirs.push_back(lib + "/client");
            for (const std::string& arch : ArchitectureNames())
                dirs.push_back(lib + "/" + arch);
            dirs.push_back(lib + "/jli");
            dirs.push_back(lib);
            return dirs;
        }

        // Every library file in one directory, sorted so two runs open them in the same order. Nothing found
        // here is loaded for its own sake - each file still goes through the same DT_NEEDED walk, and a
        // dependency that cannot be found is still one log line - but the scan is what reaches the libraries
        // the Java side asks for later by name.
        std::vector<std::string> SharedObjectsIn(const std::string& dir) {
            std::vector<std::string> found;

            DIR* handle = opendir(dir.c_str());
            if (handle == nullptr)
                return found;

            while (struct dirent* entry = readdir(handle)) {
                const std::string name = entry->d_name;
                if (name.size() <= 3 || name.compare(name.size() - 3, 3, ".so") != 0)
                    continue;
                std::string path = dir + "/" + name;
                if (util::File::IsRegular(path))
                    found.push_back(path);
            }
            closedir(handle);

            std::sort(found.begin(), found.end());
            return found;
        }

        // One of the two libraries this bridge calls into, or an empty path when this JRE does not ship it.
        // Everything else is discovered.
        std::string FindEntryPoint(const std::vector<std::string>& dirs, const char* soname) {
            for (const std::string& dir : dirs) {
                std::string candidate = dir + "/" + soname;
                if (util::File::IsRegular(candidate))
                    return candidate;
            }
            return std::string();
        }

    } // namespace

    void LoadJreLibraries(JNIEnv* env, jclass, jstring jreDir) {
        const std::string root = util::Jni::ToString(env, jreDir);
        if (root.empty()) {
            Log::Error("LOADER", "loadJvmLibs called without a JRE directory");
            return;
        }

        const std::vector<std::string> dirs = LayoutDirs(root);
        util::Linker linker(dirs);

        Log::InfoF("LOADER", "JRE %s", root.c_str());
        Log::Info("LOADER", "search path:");
        for (const std::string& dir : dirs)
            Log::InfoF("LOADER", "  %s", dir.c_str());

        // libjli holds JLI_Launch, libjvm holds JNI_CreateJavaVM. Resolving both up front turns a broken JRE
        // layout into an immediate, clear report instead of a failure halfway through the launch.
        const std::string jli = FindEntryPoint(dirs, "libjli.so");
        const std::string jvm = FindEntryPoint(dirs, "libjvm.so");

        bool ok = true;
        if (jli.empty()) {
            Log::Error("LOADER", "libjli.so not found under this JRE, the JVM cannot be started");
            ok = false;
        }
        if (jvm.empty()) {
            Log::Error("LOADER", "libjvm.so not found under this JRE, no Java VM can be created");
            ok = false;
        }
        if (!ok)
            return;

        // Opened by absolute path, which is what makes the later loads by name work: both entry points are
        // answered out of the linker's list of what is already mapped rather than out of a search path. Each
        // is recorded under its path relative to the JRE's lib directory.
        const std::string libDir = root + "/lib/";
        const auto remember = [&libDir](const std::string& path, void* handle) {
            if (handle == nullptr)
                return;
            const std::string relative = path.compare(0, libDir.size(), libDir) == 0 ? path.substr(libDir.size())
            : util::File::BaseName(path);
            jreLibraries[relative] = handle;
        };

        remember(jli, linker.Load(jli));
        remember(jvm, linker.Load(jvm));

        // The two entry points do not reach everything a running JVM asks for: libnet.so is needed by
        // libnio.so alone, and only once the Java side touches java.nio.file - by then the JVM calls
        // dlopen("libnet.so") itself, and bionic cannot resolve that name, since the JRE directory is not on
        // its search path. Opening the file here makes that same call resolve to the copy in memory.
        // Architecture directory first: a JRE keeps the architecture specific build of a shared name there.
        std::vector<std::string> onDemandDirs;
        for (const std::string& arch : ArchitectureNames())
            onDemandDirs.push_back(root + "/lib/" + arch);
        onDemandDirs.push_back(root + "/lib");

        int seeded = 0;
        for (const std::string& dir : onDemandDirs) {
            for (const std::string& path : SharedObjectsIn(dir)) {
                if (path == jli || path == jvm)
                    continue;
                remember(path, linker.Load(path));
                seeded++;
            }
        }
        Log::InfoF("LOADER", "seeded %d on demand libraries", seeded);

        const util::Linker::Stats stats = linker.Statistics();
        Log::InfoF("LOADER", "loaded %d libraries, skipped %d", stats.loaded, stats.skipped);
        if (!ok) {
            Log::Error("LOADER", "this JRE cannot be used to start the JVM");
            return;
        }

        Log::InfoF("LOADER", "the JRE is %d of the %d libraries this loader opened",
                 static_cast<int>(jreLibraries.size()), static_cast<int>(linker.LoadedPaths().size()));

        // Hooks last, and only now: the hook library takes its snapshot of what is loaded when it starts.
        Hook::PrepareHooks();
        for (const auto& entry : jreLibraries)
            Hook::InstallHook(entry.first);
    }

    void UpdateLinkerPath(JNIEnv* env, jclass, jstring path) {
        const std::string value = util::Jni::ToString(env, path);
        if (value.empty())
            return;

        // A private libdl entry point, present in the sphal namespace on some ROMs. Best effort by nature:
        // the dependency walk in LoadJreLibraries is what actually makes the loads work.
        void* libdl = dlopen("libdl.so", RTLD_LAZY);
        if (libdl == nullptr) {
            Log::Warn("LOADER", "cannot open libdl.so, skipping the linker path update");
            return;
        }

        using UpdateLdPath = void (*)(const char*);
        auto update = reinterpret_cast<UpdateLdPath>(dlsym(libdl, "android_update_LD_LIBRARY_PATH"));
        if (update == nullptr)
            update = reinterpret_cast<UpdateLdPath>(
                                        dlsym(libdl, "__loader_android_update_LD_LIBRARY_PATH"));

        if (update == nullptr) {
            Log::Warn("LOADER", "this ROM has no android_update_LD_LIBRARY_PATH, relying on absolute path loads");
        } else {
            update(value.c_str());
            Log::InfoF("LOADER", "linker path updated: %s", value.c_str());
        }
        dlclose(libdl);
    }

    void SetEnv(JNIEnv* env, jclass, jstring key, jstring value) {
        const std::string name = util::Jni::ToString(env, key);
        if (name.empty())
            return;
        setenv(name.c_str(), util::Jni::ToString(env, value).c_str(), 1);
    }

} // namespace copper::bridge::jre::Loader
