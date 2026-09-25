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

// Taking over the VM's exit, its output streams, and the opens that would write the log file.
//
// The VM's exit() runs the destructors of every library loaded here, the platform's included, and an
// ART-side thread that touches that state afterwards dies of FORTIFY and SIGABRT. So the process has to
// end before exit() gets anywhere, and exit() is taken over by patching the GOT slot of the libraries
// that call it: bionic does not promote an already loaded library to the global scope, so a symbol of
// ours would never be reached (measured). Going per-library is also what keeps ART's own exits out.

namespace copper::bridge::jre::Hook {
    namespace {

        /** The VM's exit: the process ends here, before its teardown can run. */
        void ExitFromJvm(int status) {
            util::Log::InfoF("HOOK", "the VM's exit(%d) reached the hook", status);
            Launcher::EndProcessNow(status);
        }

        // A non-null stub only means the request was accepted: NOSYM is the only sign that a library never
        // referenced the symbol.
        void HookedCallback(bytehook_stub_t stub, int status, const char* callerPathName, const char* symName,
                    void* newFunc, void* prevFunc, void* arg) {
            (void)stub;
            (void)newFunc;
            (void)prevFunc;
            (void)arg;
            if (status == BYTEHOOK_STATUS_CODE_NOSYM)
                return;

            util::Log::VerboseF("HOOK", "hook %s in %s: status %d", symName != nullptr ? symName : "?",
                  callerPathName != nullptr ? callerPathName : "?", status);
        }

        // The calls the process's own output is written through.
        //
        // Only the JRE's libraries are hooked, never ART's and never this one: that is what makes the
        // pass-through below a plain libc call with no trampoline lookup, and hooking every caller crashed
        // because android::Looper::wake() writes on every ART message.
        //
        // Only fd 1 and 2 (and stdout/stderr) are taken; a formatted line is cut at MAX_FORMATTED_LENGTH.

        constexpr size_t MAX_FORMATTED_LENGTH = 4096;

        bool IsOwnStream(FILE* stream) {
            return stream == stdout || stream == stderr;
        }

        int FeedFormatted(int streamIndex, const char* format, va_list args) {
            char buffer[MAX_FORMATTED_LENGTH];
            const int length = vsnprintf(buffer, sizeof(buffer), format, args);
            if (length <= 0)
                return length;

            const size_t whole = static_cast<size_t>(length) < sizeof(buffer) ? static_cast<size_t>(length)
            : sizeof(buffer) - 1;
            util::Log::FeedStream(streamIndex, buffer, whole);
            return length;
        }

        ssize_t WriteProxy(int fd, const void* buffer, size_t count) {
            if ((fd == STDOUT_FILENO || fd == STDERR_FILENO) && buffer != nullptr) {
                util::Log::FeedStream(fd - STDOUT_FILENO, static_cast<const char*>(buffer), count);
                return static_cast<ssize_t>(count);
            }
            return ::write(fd, buffer, count);
        }

        size_t FwriteProxy(const void* ptr, size_t size, size_t nmemb, FILE* stream) {
            if (IsOwnStream(stream) && ptr != nullptr) {
                util::Log::FeedStream(stream == stdout ? 0 : 1, static_cast<const char*>(ptr), size * nmemb);
                return nmemb;
            }
            return ::fwrite(ptr, size, nmemb, stream);
        }

        int VfprintfProxy(FILE* stream, const char* format, va_list args) {
            if (IsOwnStream(stream) && format != nullptr)
                return FeedFormatted(stream == stdout ? 0 : 1, format, args);
            return ::vfprintf(stream, format, args);
        }

        int PrintfProxy(const char* format, ...) {
            va_list args;
            va_start(args, format);
            const int length = format != nullptr ? FeedFormatted(0, format, args) : 0;
            va_end(args);
            return length;
        }

        int FprintfProxy(FILE* stream, const char* format, ...) {
            va_list args;
            va_start(args, format);
            // The pass-through goes through vfprintf: a va_list cannot be expanded into a variadic call again.
            const int length = IsOwnStream(stream) && format != nullptr
            ? FeedFormatted(stream == stdout ? 0 : 1, format, args)
            : ::vfprintf(stream, format, args);
            va_end(args);
            return length;
        }

        // The open family, so that nothing but this bridge can write into the file a run logs into.
        //
        // The host's own loader opens that same path with `new FileOutputStream(file, false)`, truncating the
        // file first. Those lines are not the bridge's, so such an open is neither refused nor rewritten -
        // either would break a loader - but made on /dev/null instead: a valid descriptor whose writes go
        // nowhere and raise nothing. Reading is untouched, and so is O_TMPFILE, which never names this file.
        //
        // One path only, and it is the one this library was handed for its log: the same file reaches the
        // process as /data/user/0/<pkg> and /data/data/<pkg>, so both spellings match and nothing else does.
        // `unlink` and `remove` are deliberately not taken - a round's starter deletes the log on purpose.

        // The two heads an app's private directory is reached by. What follows either of them is the same.
        const std::string USER_DIRECTORY = "/data/user/0/";
        const std::string DATA_DIRECTORY = "/data/data/";

        // A write open of the log is made here instead: it throws away whatever is written to it.
        constexpr const char* NULL_DEVICE = "/dev/null";

        // Whether a path is the log file's, under either name this process reaches it by. The other name is
        // built from the log's own path rather than guessed, so nothing outside this app's files directory
        // can match.
        bool IsLogPath(const char* path) {
            if (path == nullptr)
                return false;

            const std::string& log = util::Log::Path();
            if (log.empty())
                return false;
            if (log == path)
                return true;

            // The same file under the directory's other name. The head of the path in hand is tested first,
            // so a path that is not the log's is turned away by a byte compare.
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

        // What an open of the log becomes.
        enum class Where {
            File,
            NullDevice,
            // A file with no name holding a copy of the log: a read-write open keeps reading, at the cost of
            // seeing the log as of the open, and its writes go nowhere.
            Copy,
        };

        // What an open of that path becomes: where it is made, with which flags, and by which of the three
        // ways above. The redirect drops only the flags that mean something for creating or emptying a file -
        // /dev/null is always there, and O_APPEND on it means nothing - so O_CLOEXEC and O_NONBLOCK survive.
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

            // The access mode left is O_RDWR, which has to keep reading.
            return Target{path, flags, Where::Copy};
        }

        // How much of the log is copied into a sink at one time. One page, and the size the formatted line
        // buffer above is given too.
        constexpr size_t COPY_BYTES = 4096;

        // A file nothing else can reach, so its bytes cannot be found once the last descriptor to it is
        // closed: memfd_create (API 30, what this library is built for), or O_TMPFILE in the log's own
        // directory where the filesystem supports it. The caller's own O_CLOEXEC is carried over.
        int AnonymousFile(int flags) {
            const unsigned int noInherit = (flags & O_CLOEXEC) != 0 ? MFD_CLOEXEC : 0U;
            const int memory = ::memfd_create("copper-log", noInherit);
            if (memory >= 0)
                return memory;

            const std::string directory = util::File::DirName(util::Log::Path());
            return ::open(directory.c_str(), O_TMPFILE | O_RDWR | (flags & O_CLOEXEC), 0600);        }

        // The sink a read-write open is served from: an anonymous file holding the log's bytes as of this
        // moment, handed back at offset 0. The copy is made with a plain libc open from this library, which is
        // never hooked, so there is no recursion.
        //
        // Both failure modes refuse the open rather than hand a writer this file: no anonymous file could be
        // made, or the copy could not be completed. The caller gets that errno.
        int Snapshot(const char* path, int flags) {
            const int sink = AnonymousFile(flags);
            if (sink < 0) {
                const int failure = errno;
                util::Log::WarnF("HOOK", "no anonymous file for a read-write open of %s (memfd_create and O_TMPFILE "
                        "both failed), refusing it: errno %d", path, failure);
                errno = failure;
                return -1;
            }

            // The failure is taken where it happens: closing a descriptor may set errno too, and the caller
            // must get the reason its own open failed.
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
            util::Log::WarnF("HOOK", "cannot fill the sink for a read-write open of %s, refusing it: errno %d", path,
                    failure);
            errno = failure;
            return -1;
        }

        // libc's own condition, and it has to be the same one: the mode is read under exactly the flags that
        // made the caller pass it. O_TMPFILE shares bits with O_DIRECTORY, so it is its own test; reading a
        // mode under any other flag takes a register the caller never set.
        bool HasMode(int flags) {
            return (flags & O_CREAT) != 0 || (flags & O_TMPFILE) == O_TMPFILE;
        }

        // Says, once per write open of the log, that it was given a sink instead of the file, and which kind.
        // Verbose, because the file is what says whether the rule held, not Android's log; the path named is
        // the one asked for, since the sink is what came of it.
        void ReportDiscarded(const char* path, Where where) {
            util::Log::VerboseF("HOOK", "open of %s for writing: writes are discarded and the log keeps its lines; "
                    "the descriptor is %s", path != nullptr ? path : "?",
                    where == Where::NullDevice ? "/dev/null" : "an anonymous copy of the log");
        }

        // The request an open was made with: the call it becomes, and the mode the flags say it carried.
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

        // The proxies. Each one calls that name in libc - this library is never hooked, so there is no
        // trampoline - except the copy arm, which is built here.
        //
        // The call carries a mode whether or not the caller passed one: libc reads it only under the flags
        // HasMode asks about, and the two argument form the fortify'd header makes of it aborts when the flags
        // turn out to want a mode.
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

        // The two argument forms the fortify'd header makes of a call that passes no mode.
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

        // Everything this module takes over, in one table so the walk below hooks them together.
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
            // The fortify'd forms belong with the plain ones: a caller built with _FORTIFY_SOURCE - every
            // platform library is - reaches __open_2 for a two argument open and has its three argument open
            // inlined onto `open`, so which of these a library calls depends on how it was built.
            {"open", reinterpret_cast<void*>(OpenProxy)},
            {"open64", reinterpret_cast<void*>(Open64Proxy)},
            {"openat", reinterpret_cast<void*>(OpenatProxy)},
            {"openat64", reinterpret_cast<void*>(Openat64Proxy)},
            {"__open_2", reinterpret_cast<void*>(Open2Proxy)},
            {"__openat_2", reinterpret_cast<void*>(Openat2Proxy)},
        };

        // Whether the hook library has been initialized, so a second PrepareHooks is a no-op.
        bool prepared = false;

    } // namespace

    void PrepareHooks() {
        // Once, and only after the loading: ByteHook can only hook the libraries it saw when it started
        // (measured: hooking libjvm right after loading it, with the init done on libjli a moment earlier,
        // installed nothing).
        if (prepared)
            return;

        if (bytehook_init(BYTEHOOK_MODE_MANUAL, false) != 0) {
            util::Log::Warn("HOOK", "cannot initialize the hook library, so the VM keeps its exit and its streams "
                    "and any writer may write the log file");
            return;
        }
        prepared = true;
    }

    void InstallHook(const std::string& library) {
        // The name, not the path: ByteHook matches a bare name by suffix, and the JRE directory reaches this
        // process under two names.
        const std::string name = util::File::BaseName(library);

        int hooked = 0;
        for (const Taken& taken : TAKEN) {
            if (bytehook_hook_single(name.c_str(), nullptr, taken.symbol, taken.replacement,
                         HookedCallback, nullptr) != nullptr)
                hooked++;
        }

        util::Log::VerboseF("HOOK", "asked for %d symbols in %s", hooked, name.c_str());
    }

} // namespace copper::bridge::jre::Hook