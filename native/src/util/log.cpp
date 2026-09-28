#include "util/log.h"

#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>

namespace copper::bridge::util::Log {

    namespace {

        struct Side {
            const char* letter;
            const char* logcatTag;
        };

        // This library's side, and the game's; the ART side's Java never reaches this code.
        constexpr Side NATIVE_SIDE{"N", "CopperBridgeNative"};
        constexpr Side GAME_SIDE{"G", "CopperBridgeGame"};

        // The log file, filled in by `OpenFile`; `fileMutex` keeps several threads' writes apart.
        int logFd = -1;

        std::string logPath;

        bool logcat = false;
        std::mutex fileMutex;

        Level logLevel = Level::INFO;

        // Long lines become several lines, cut below the logcat cap so the straddling characters arrive whole.
        constexpr size_t LINE_BYTES = 1000;

        struct Stream {
            android_LogPriority priority = ANDROID_LOG_INFO;
            std::string line;

            // Assembling a line is a read-modify-write, done by any thread that called a hooked write.
            // One mutex per stream, taken before the file's, never after.
            std::mutex mutex;
        };

        Stream streams[2];

        // The logcat copy of one line: written when it was asked for, or when the line says something is broken.
        void Logcat(android_LogPriority priority, const char* logcatTag, const std::string& line) {
            if (logcat || priority >= ANDROID_LOG_ERROR)
                __android_log_write(priority, logcatTag, line.c_str());
        }

        const char* LetterFor(Level level) {
            switch (level) {
                case Level::VERBOSE: return "[V]";
                case Level::DEBUG:   return "[D]";
                case Level::WARN:    return "[W]";
                case Level::ERROR:   return "[E]";
                case Level::INFO:
                default:             return "[I]";
            }
        }

        android_LogPriority ConvertLevel(Level level) {
            switch (level) {
                case Level::VERBOSE: return ANDROID_LOG_VERBOSE;
                case Level::DEBUG:   return ANDROID_LOG_DEBUG;
                case Level::WARN:    return ANDROID_LOG_WARN;
                case Level::ERROR:   return ANDROID_LOG_ERROR;
                case Level::INFO:
                default:             return ANDROID_LOG_INFO;
            }
        }

        Level ConvertLevel(android_LogPriority priority) {
            switch (priority) {
                case ANDROID_LOG_VERBOSE: return Level::VERBOSE;
                case ANDROID_LOG_DEBUG:   return Level::DEBUG;
                case ANDROID_LOG_WARN:    return Level::WARN;
                case ANDROID_LOG_ERROR:   return Level::ERROR;
                case ANDROID_LOG_INFO:
                default:                  return Level::INFO;
            }
        }

        constexpr size_t HEAD_LENGTH = 4;

        Level HeadLevel(const std::string& line) {
            if (line.size() < HEAD_LENGTH || line[0] != '[' || line[2] != ']' || line[3] != ' ')
                return Level::UNKNOWN;
            switch (line[1]) {
                case 'V': return Level::VERBOSE;
                case 'D': return Level::DEBUG;
                case 'I': return Level::INFO;
                case 'W': return Level::WARN;
                case 'E': return Level::ERROR;
                default:  return Level::UNKNOWN;
            }
        }

        // Writes a whole line over as many calls as the kernel needs, the mutex held across all of them.
        void WriteAll(int fd, const std::string& line) {
            size_t written = 0;
            while (written < line.size()) {
                const ssize_t count = write(fd, line.c_str() + written, line.size() - written);
                if (count > 0) {
                    written += static_cast<size_t>(count);
                    continue;
                }
                if (count < 0 && errno == EINTR)
                    continue;
                return;
            }
        }

        // Appends one whole line under the file's lock: an appending write is atomic on one filesystem, not by standard.
        void AppendToFile(const std::string& entry) {
            const std::string line = entry + '\n';
            std::lock_guard<std::mutex> guard(fileMutex);
            if (logFd < 0)
                return;
            WriteAll(logFd, line);
        }

        void FlushLineLocked(Stream& stream) {
            if (stream.line.empty())
                return;
            // A captured line may carry its own level, which then comes out of the message; the side is the game's.
            Level level = ConvertLevel(stream.priority);
            std::string message = stream.line;
            Level declared = HeadLevel(stream.line);
            if (declared >= 0 && &stream != &streams[1]) {
                level = declared;
                message = stream.line.substr(HEAD_LENGTH);
            }

            const std::string head = std::string(LetterFor(level)) + "[" + GAME_SIDE.letter + "] ";
            LogLine(level, GAME_SIDE.logcatTag, head + message);
            stream.line.clear();
        }

        void AddByteLocked(Stream& stream, char c) {
            if (c == '\n') {
                FlushLineLocked(stream);
                return;
            }
            if (c == '\r')
                return;

            // Cut at a character boundary: a cut inside a UTF-8 sequence would arrive as two broken characters.
            if (stream.line.size() >= LINE_BYTES && (static_cast<unsigned char>(c) & 0xC0) != 0x80)
                FlushLineLocked(stream);
            stream.line.push_back(c);
        }

    } // namespace

    bool OpenFile(const std::string& path, bool wanted) {
        std::lock_guard<std::mutex> guard(fileMutex);
        if (logFd >= 0)
            return true;
        if (path.empty())
            return false;

        logFd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (logFd < 0)
            return false;

        logPath = path;

        logcat = wanted;

        streams[StreamType::STDOUT].priority = ANDROID_LOG_INFO;
        streams[StreamType::STDERR].priority = ANDROID_LOG_ERROR;
        return true;
    }

    void SetLevel(Level level) {
        logLevel = level;
    }

    const std::string& Path() {
        return logPath;
    }

    void LogLine(Level level, const char* logcatTag, const std::string& line) {
        if (level > logLevel)
            return;
        Logcat(ConvertLevel(level), logcatTag, line);
        AppendToFile(line);
    }

    namespace {

        constexpr size_t MAX_MESSAGE_LENGTH = 4096;

        void NativeLine(Level level, const char* tag, const std::string& message) {
            if (level > logLevel)
                return;
            const std::string brackets = tag == NO_TAG ? "" : std::string(" [") + tag + "]";
            LogLine(level, NATIVE_SIDE.logcatTag,
                    std::string(LetterFor(level)) + "[" + NATIVE_SIDE.letter + "]" + brackets + " " + message);
        }

        std::string FormatMessage(const char* format, va_list args) {
            char buffer[MAX_MESSAGE_LENGTH];
            vsnprintf(buffer, sizeof(buffer), format, args);
            return std::string(buffer);
        }

    } // namespace

    void LogGameStream(StreamType type, const char* bytes, size_t length) {
        if (bytes == nullptr || type < 0 || type > 1)
            return;

        Stream& stream = streams[type];
        std::lock_guard<std::mutex> guard(stream.mutex);
        for (size_t i = 0; i < length; i++)
            AddByteLocked(stream, bytes[i]);
    }

    void Verbose(const char* tag, const std::string& message) { NativeLine(Level::VERBOSE, tag, message); }
    void Debug(const char* tag, const std::string& message) { NativeLine(Level::DEBUG, tag, message); }
    void Info(const char* tag, const std::string& message) { NativeLine(Level::INFO, tag, message); }
    void Warn(const char* tag, const std::string& message) { NativeLine(Level::WARN, tag, message); }
    void Error(const char* tag, const std::string& message) { NativeLine(Level::ERROR, tag, message); }

    void VerboseF(const char* tag, const char* format, ...) {
        va_list args;
        va_start(args, format);
        const std::string message = FormatMessage(format, args);
        va_end(args);
        Verbose(tag, message);
    }

    void DebugF(const char* tag, const char* format, ...) {
        va_list args;
        va_start(args, format);
        const std::string message = FormatMessage(format, args);
        va_end(args);
        Debug(tag, message);
    }

    void InfoF(const char* tag, const char* format, ...) {
        va_list args;
        va_start(args, format);
        const std::string message = FormatMessage(format, args);
        va_end(args);
        Info(tag, message);
    }

    void WarnF(const char* tag, const char* format, ...) {
        va_list args;
        va_start(args, format);
        const std::string message = FormatMessage(format, args);
        va_end(args);
        Warn(tag, message);
    }

    void ErrorF(const char* tag, const char* format, ...) {
        va_list args;
        va_start(args, format);
        const std::string message = FormatMessage(format, args);
        va_end(args);
        Error(tag, message);
    }

} // namespace copper::bridge
