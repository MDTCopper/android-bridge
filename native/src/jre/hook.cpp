#include "jre/hook.h"

#include "jre/internal/launcher.h"
#include "util/file.h"
#include "util/log.h"

#include <bytehook.h>

#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>

// Takes the VM's exit, its output streams and the log file's opens over.
// exit() runs the destructors of every library loaded here; an ART thread touching that state dies of FORTIFY/SIGABRT.
// Bionic does not promote a loaded library to global scope (measured), so the GOT slot is patched instead.

namespace copper::bridge::jre::Hook {
    namespace {
        namespace Log = util::Log;

        void ExitFromJvm(int status) {
            Log::InfoF("HOOK", "the VM's exit(%d) reached the hook", status);
            Launcher::EndProcessNow(status);
        }

        void HookedCallback(bytehook_stub_t stub, int status, const char* callerPathName, const char* symName,
                    void* newFunc, void* prevFunc, void* arg) {
            (void)stub;
            (void)newFunc;
            (void)prevFunc;
            (void)arg;
            if (status == BYTEHOOK_STATUS_CODE_NOSYM)
                return;

            Log::VerboseF("HOOK", "hook %s in %s: status %d", symName != nullptr ? symName : "?",
                  callerPathName != nullptr ? callerPathName : "?", status);
        }

        // Only the JRE's libraries are hooked, never ART's: that keeps the pass-through a plain libc call.

        constexpr size_t MAX_FORMATTED_LENGTH = 4096;
        bool IsOwnStream(FILE* stream) {
            return stream == stdout || stream == stderr;
        }

        Log::StreamType StreamTypeFromStream(FILE* stream) {
            return stream == stderr ? Log::StreamType::STDERR : Log::StreamType::STDOUT;
        }

        Log::StreamType StreamTypeFromFd(int fd) {
            return fd == STDERR_FILENO ? Log::StreamType::STDERR : Log::StreamType::STDOUT;
        }

        int FeedFormatted(Log::StreamType streamType, const char* format, va_list args) {
            char buffer[MAX_FORMATTED_LENGTH];
            const int length = vsnprintf(buffer, sizeof(buffer), format, args);
            if (length <= 0)
                return length;

            const size_t whole = static_cast<size_t>(length) < sizeof(buffer) ? static_cast<size_t>(length)
            : sizeof(buffer) - 1;
            Log::LogGameStream(streamType, buffer, whole);
            return length;
        }

        ssize_t WriteProxy(int fd, const void* buffer, size_t count) {
            if ((fd == STDOUT_FILENO || fd == STDERR_FILENO) && buffer != nullptr) {
                Log::LogGameStream(StreamTypeFromFd(fd), static_cast<const char*>(buffer), count);
                return static_cast<ssize_t>(count);
            }
            return ::write(fd, buffer, count);
        }

        size_t FwriteProxy(const void* ptr, size_t size, size_t nmemb, FILE* stream) {
            if (IsOwnStream(stream) && ptr != nullptr) {
                Log::LogGameStream(StreamTypeFromStream(stream), static_cast<const char*>(ptr), size * nmemb);
                return nmemb;
            }
            return ::fwrite(ptr, size, nmemb, stream);
        }

        int VfprintfProxy(FILE* stream, const char* format, va_list args) {
            if (IsOwnStream(stream) && format != nullptr)
                return FeedFormatted(StreamTypeFromStream(stream), format, args);
            return ::vfprintf(stream, format, args);
        }

        int PrintfProxy(const char* format, ...) {
            va_list args;
            va_start(args, format);
            const int length = format != nullptr ? FeedFormatted(Log::StreamType::STDOUT, format, args) : 0;
            va_end(args);
            return length;
        }

        int FprintfProxy(FILE* stream, const char* format, ...) {
            va_list args;
            va_start(args, format);
            // The pass-through goes through vfprintf: a va_list cannot be expanded into a variadic call again.
            const int length = IsOwnStream(stream) && format != nullptr
            ? FeedFormatted(StreamTypeFromStream(stream), format, args)
            : ::vfprintf(stream, format, args);
            va_end(args);
            return length;
        }

        // The open family, so nothing but this bridge can write into the file a run logs into. A truncating open of
        // that path is served from /dev/null; reading and O_TMPFILE are untouched.
        // `unlink` and `remove` are not taken: a starter deletes the log on purpose.

        const std::string USER_DIRECTORY = "/data/user/0/";
        const std::string DATA_DIRECTORY = "/data/data/";

        constexpr const char* NULL_DEVICE = "/dev/null";

        // Whether a path is the log file's, under either name this process reaches it by.
        bool IsLogPath(const char* path) {
            if (path == nullptr)
                return false;

            const std::string& log = Log::Path();
            if (log.empty())
                return false;
            if (log == path)
                return true;

            const size_t userLength = USER_DIRECTORY.size();
            const size_t dataLength = DATA_DIRECTORY.size();
            if (log.compare(0, userLength, USER_DIRECTORY) == 0)
                return std::strncmp(path, DATA_DIRECTORY.c_str(), dataLength) == 0
                        && DATA_DIRECTORY + log.substr(userLength) == path;
            if (log.compare(0, dataLength, DATA_DIRECTORY) == 0)
                return std::strncmp(path, USER_DIRECTORY.c_str(), userLength) == 0
                        && USER_DIRECTORY + log.substr(dataLength) == path;
            return false;
        }

        enum class Where {
            File,
            NullDevice,
            // A file with no name holding a copy of the log: a read-write open keeps reading, its writes go nowhere.
            Copy,
        };

        // What an open of that path becomes. The redirect drops only the flags that create or empty a file.
        struct Target {
            const char* path;
            int flags;
            Where where;
        };

        Target TargetOf(const char* path, int flags) {
            if (!IsLogPath(path))
                return Target{path, flags, Where::File};

            if ((flags & O_TMPFILE) == O_TMPFILE)
                return Target{path, flags, Where::File};

            if ((flags & O_ACCMODE) == O_RDONLY)
                return Target{path, flags, Where::File};

            if ((flags & O_ACCMODE) == O_WRONLY)
                return Target{NULL_DEVICE, flags & ~(O_CREAT | O_EXCL | O_TRUNC | O_APPEND), Where::NullDevice};

            return Target{path, flags, Where::Copy};
        }

        constexpr size_t COPY_BYTES = 4096;

        // Its bytes are gone once the last descriptor closes: memfd_create (API 30) or O_TMPFILE.
        int AnonymousFile(int flags) {
            const unsigned int noInherit = (flags & O_CLOEXEC) != 0 ? MFD_CLOEXEC : 0U;
            const int memory = ::memfd_create("copper-log", noInherit);
            if (memory >= 0)
                return memory;

            const std::string directory = util::File::DirName(Log::Path());
            return ::open(directory.c_str(), O_TMPFILE | O_RDWR | (flags & O_CLOEXEC), 0600);        }

        // The sink a read-write open is served from: an anonymous file holding the log's bytes, or the open is refused.
        int Snapshot(const char* path, int flags) {
            const int sink = AnonymousFile(flags);
            if (sink < 0) {
                const int failure = errno;
                Log::WarnF("HOOK", "no anonymous file for a read-write open of %s (memfd_create and O_TMPFILE "
                        "both failed), refusing it: errno %d", path, failure);
                errno = failure;
                return -1;
            }

            // The failure is taken where it happens: closing a descriptor may set errno too.
            int failure = 0;
            bool copied = true;
            const int source = ::open(path, O_RDONLY | O_CLOEXEC);
            if (source < 0) {
                failure = errno;
                copied = false;
            } else {
                char buffer[COPY_BYTES];
                ssize_t got = 0;
                while (copied && (got = ::read(source, buffer, sizeof(buffer))) != 0) {
                    if (got < 0) {
                        if (errno == EINTR)
                            continue;
                        failure = errno;
                        copied = false;
                        break;
                    }
                    ssize_t put = 0;
                    while (put < got) {
                        const ssize_t once = ::write(sink, buffer + put, static_cast<size_t>(got - put));
                        if (once > 0) {
                            put += once;
                            continue;
                        }
                        if (once < 0 && errno == EINTR)
                            continue;
                        if (once < 0)
                            failure = errno;
                        copied = false;
                        break;
                    }
                }
                ::close(source);
            }

            if (copied && ::lseek(sink, 0, SEEK_SET) >= 0)
                return sink;
            if (failure == 0)
                failure = errno;

            ::close(sink);
            Log::WarnF("HOOK", "cannot fill the sink for a read-write open of %s, refusing it: errno %d", path,
                    failure);
            errno = failure;
            return -1;
        }

        // libc's own condition: the mode is read under the caller's own flags, and O_TMPFILE is its own test.
        bool HasMode(int flags) {
            return (flags & O_CREAT) != 0 || (flags & O_TMPFILE) == O_TMPFILE;
        }

        void ReportDiscarded(const char* path, Where where) {
            Log::VerboseF("HOOK", "open of %s for writing: writes are discarded and the log keeps its lines; "
                    "the descriptor is %s", path != nullptr ? path : "?",
                    where == Where::NullDevice ? "/dev/null" : "an anonymous copy of the log");
        }

        struct Request {
            Target target;
            mode_t mode;
        };

        Request TakeRequest(const char* path, int flags, va_list args) {
            Request request{TargetOf(path, flags), 0};
            if (request.target.where != Where::File)
                ReportDiscarded(path, request.target.where);
            if (HasMode(flags))
                request.mode = static_cast<mode_t>(va_arg(args, int));
            return request;
        }

        // The proxies call that name in libc - this library is never hooked - except the copy arm.
        int OpenProxy(const char* path, int flags, ...) {
            va_list args;
            va_start(args, flags);
            const Request request = TakeRequest(path, flags, args);
            va_end(args);

            if (request.target.where == Where::Copy)
                return Snapshot(request.target.path, request.target.flags);
            return ::open(request.target.path, request.target.flags, request.mode);
        }

        int Open64Proxy(const char* path, int flags, ...) {
            va_list args;
            va_start(args, flags);
            const Request request = TakeRequest(path, flags, args);
            va_end(args);

            if (request.target.where == Where::Copy)
                return Snapshot(request.target.path, request.target.flags);
            return ::open64(request.target.path, request.target.flags, request.mode);
        }

        int OpenatProxy(int dirfd, const char* path, int flags, ...) {
            va_list args;
            va_start(args, flags);
            const Request request = TakeRequest(path, flags, args);
            va_end(args);

            if (request.target.where == Where::Copy)
                return Snapshot(request.target.path, request.target.flags);
            return ::openat(dirfd, request.target.path, request.target.flags, request.mode);
        }

        int Openat64Proxy(int dirfd, const char* path, int flags, ...) {
            va_list args;
            va_start(args, flags);
            const Request request = TakeRequest(path, flags, args);
            va_end(args);

            if (request.target.where == Where::Copy)
                return Snapshot(request.target.path, request.target.flags);
            return ::openat64(dirfd, request.target.path, request.target.flags, request.mode);
        }

        int Open2Proxy(const char* path, int flags) {
            const Target target = TargetOf(path, flags);
            if (target.where != Where::File)
                ReportDiscarded(path, target.where);

            if (target.where == Where::Copy)
                return Snapshot(target.path, target.flags);
            return ::__open_2(target.path, target.flags);
        }

        int Openat2Proxy(int dirfd, const char* path, int flags) {
            const Target target = TargetOf(path, flags);
            if (target.where != Where::File)
                ReportDiscarded(path, target.where);

            if (target.where == Where::Copy)
                return Snapshot(target.path, target.flags);
            return ::__openat_2(dirfd, target.path, target.flags);
        }

        struct Taken {
            const char* symbol;
            void* replacement;
        };

        const Taken TAKEN[] = {
            {"exit", reinterpret_cast<void*>(ExitFromJvm)},
            {"write", reinterpret_cast<void*>(WriteProxy)},
            {"fwrite", reinterpret_cast<void*>(FwriteProxy)},
            {"vfprintf", reinterpret_cast<void*>(VfprintfProxy)},
            {"fprintf", reinterpret_cast<void*>(FprintfProxy)},
            {"printf", reinterpret_cast<void*>(PrintfProxy)},
            // The fortify'd forms belong with the plain ones: _FORTIFY_SOURCE callers reach __open_2.
            {"open", reinterpret_cast<void*>(OpenProxy)},
            {"open64", reinterpret_cast<void*>(Open64Proxy)},
            {"openat", reinterpret_cast<void*>(OpenatProxy)},
            {"openat64", reinterpret_cast<void*>(Openat64Proxy)},
            {"__open_2", reinterpret_cast<void*>(Open2Proxy)},
            {"__openat_2", reinterpret_cast<void*>(Openat2Proxy)},
        };

        bool prepared = false;

    } // namespace

    void PrepareHooks() {
        // Once, and only after the loading: ByteHook can only hook the libraries it saw when it started (measured).
        if (prepared)
            return;

        if (bytehook_init(BYTEHOOK_MODE_MANUAL, false) != 0) {
            Log::Warn("HOOK", "cannot initialize the hook library, so the VM keeps its exit and its streams "
                    "and any writer may write the log file");
            return;
        }
        prepared = true;
    }

    void InstallHook(const std::string& library) {
        // The name, not the path: ByteHook matches a bare name by suffix.
        const std::string name = util::File::BaseName(library);

        int hooked = 0;
        for (const Taken& taken : TAKEN) {
            if (bytehook_hook_single(name.c_str(), nullptr, taken.symbol, taken.replacement,
                         HookedCallback, nullptr) != nullptr)
                hooked++;
        }

        Log::DebugF("HOOK", "asked for %d symbols in %s", hooked, name.c_str());
    }

} // namespace copper::bridge::jre::Hook