#ifndef COPPER_BRIDGE_LOG_H
#define COPPER_BRIDGE_LOG_H

#include <cstdint>
#include <string>

namespace copper::bridge::util::Log {

    // The levels, most severe first: a line above the one the log was given is dropped, so this order is
    // what that comparison reads, and it is Java's `Log.Level` order as well - Java hands its level over
    // as an ordinal. `UNKNOWN` is for a line that declares no level, and sits below every real one.
    enum Level : int8_t {
        UNKNOWN = -1,
        ERROR = 0, WARN, INFO, DEBUG, VERBOSE
    };

    enum StreamType : uint8_t {
        STDOUT = 0,
        STDERR
    };

    // The native side's log: the one file a run writes, and Android's log when it was asked for.
    //
    // Nothing is queued and nothing is forwarded between the two VMs: a line is written where and when it
    // was produced. The file has one writer at a time - this library from the moment it is loaded, and the
    // ART side's Java before that, which hands it over just before this library's load.
    //
    // It also holds what the process's own output streams were given: an app process runs with fd 0/1/2 on
    // /dev/null, so everything a VM prints would otherwise be thrown away.

    // Opens the log file, once, and remembers whether lines may also go to Android's log. A second call
    // finds the file already open and does nothing: the JVM loads this same copy of the library into the
    // same process. False means the file could not be opened - an empty path included - and the caller has
    // nothing to report through; whoever handed the path over says what went wrong.
    bool OpenFile(const std::string& path, bool wanted);

    // The level the log writes at: a line above it is dropped, whether it is this library's own or one
    // that arrived from Java or from a captured stream. Java's, asked for when the log is set up.
    void SetLevel(Level level);

    // Where the log file is: the path `OpenFile` was handed, and empty while there is none. The hooks that
    // guard the file recognise it by path, and they ask for it here rather than keeping a copy that could
    // disagree with the file that was actually opened.
    const std::string& Path();

    // Hands bytes written to one of the process's own streams to the log, one line at a time: `STDOUT` or
    // `STDERR`, and a line that carries arc's own `[I] ` head is levelled by it. Safe from any thread.
    void LogGameStream(StreamType type, const char* bytes, size_t length);

    // Writes one line that is already the file's text: into the file as it stands, and to Android's log when it
    // was asked for. The tag names the side that produced the line, and the level is the log's own: a line
    // above the one Java set is dropped.
    void LogLine(Level level, const char* logcatTag, const std::string& line);

    // Passed as the tag of a line that names no subsystem: the head is then the level and the side alone. The
    // side letter already says whose line it is, so a tag is only worth its brackets when it names something
    // the side does not - `BUS`, `LOADER`, `HOOK`, `JNI`, `GL` and the like.
    constexpr const char* NO_TAG = nullptr;

    // This library's own line, one function per level. `tag` names the subsystem that produced it, or is
    // `NO_TAG`, and the file's text is built from the level letter, this library's side letter and that tag.
    // The `F` forms take a printf style message, kept to a bounded size.
    void Verbose(const char* tag, const std::string& message);
    void Debug(const char* tag, const std::string& message);
    void Info(const char* tag, const std::string& message);
    void Warn(const char* tag, const std::string& message);
    void Error(const char* tag, const std::string& message);

    void VerboseF(const char* tag, const char* format, ...) __attribute__((format(printf, 2, 3)));
    void DebugF(const char* tag, const char* format, ...) __attribute__((format(printf, 2, 3)));
    void InfoF(const char* tag, const char* format, ...) __attribute__((format(printf, 2, 3)));
    void WarnF(const char* tag, const char* format, ...) __attribute__((format(printf, 2, 3)));
    void ErrorF(const char* tag, const char* format, ...) __attribute__((format(printf, 2, 3)));

} // namespace copper::bridge::util::Log

#endif // COPPER_BRIDGE_LOG_H
