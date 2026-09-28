#ifndef COPPER_BRIDGE_LOG_H
#define COPPER_BRIDGE_LOG_H

#include <cstdint>
#include <string>

namespace copper::bridge::util::Log {

    // The levels, most severe first: the comparison that drops a line above the log's own level reads this order, and
    // it is Java's `Log.Level` order too. `UNKNOWN` means no level.
    enum Level : int8_t {
        UNKNOWN = -1,
        ERROR = 0, WARN, INFO, DEBUG, VERBOSE
    };

    enum StreamType : uint8_t {
        STDOUT = 0,
        STDERR
    };

    // The native side's log: the one file a run writes, and Android's log when it was asked for, with one writer at a
    // time - this library once loaded, the ART side's Java before that.

    // Opens the log file once, and remembers whether lines may also go to Android's log. False means it could not be.
    bool OpenFile(const std::string& path, bool wanted);

    // The level the log writes at: a line above it is dropped, wherever it came from. Java's, asked for at setup.
    void SetLevel(Level level);

    // Where the log file is: the path `OpenFile` was handed. The open hooks recognise the file by it.
    const std::string& Path();

    // Hands bytes written to one of the process's own streams to the log, one line at a time. Safe from any thread.
    void LogGameStream(StreamType type, const char* bytes, size_t length);

    // Writes one line that is already the file's text, into the file and to Android's log when it was asked for.
    void LogLine(Level level, const char* logcatTag, const std::string& line);

    constexpr const char* NO_TAG = nullptr;

    // This library's own line, one function per level. The `F` forms take a printf style message.
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
