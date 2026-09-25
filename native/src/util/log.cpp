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

        // One side of the process, as the log writes it: the letter the file names it with, and the tag its
        // lines carry in Android's log. Both come from one value, because the two are the same fact.
        struct Side {
            const char* letter;
            const char* logcatTag;
        };

        // This library's own side, and the game's - the side the captured streams belong to. The ART side's Java
        // never reaches this code: it builds its own line and hands it over.
        constexpr Side NATIVE_SIDE{"N", "CopperBridgeNative"};
        constexpr Side GAME_SIDE{"G", "CopperBridgeGame"};

        // The log file, and whether Android's log gets a copy of every line. One descriptor for the
        // process: `OpenFile` fills both in from the path and the flag the Java side hands over.
        //
        // Several threads write that one descriptor, so `fileMutex` keeps their writes apart: a line is
        // built as one string and written with one `write`, and the kernel's atomicity for an appending
        // write is an implementation detail of one filesystem, not something this code may lean on.
        int logFd = -1;

        // The path that descriptor is on, so the open hooks can recognise this one file. Set by the same
        // call that sets `logFd`, so the two always name the same file.
        std::string logPath;

        bool logcat = false;
        std::mutex fileMutex;

        // The length a log line is cut at. Long lines do not lose their tail: they become several lines. The
        // cut is below the logcat cap, so the characters straddling it are whole in one of the two.
        constexpr size_t LINE_BYTES = 1000;

        // One of the process's two output streams: the priority its lines carry unless a line declares one, and
        // the line being assembled from what was written to it. Neither carries a tag - both are the game's.
        struct Stream {
            int priority = ANDROID_LOG_INFO;
            std::string line;

            // Assembling a line is a read-modify-write of `line`, and any thread that called a hooked write
            // does it. One mutex per stream, taken before the file's, never after.
            std::mutex mutex;
        };

        // Both exist with this library rather than with the first line: a hooked write can arrive before
        // `OpenFile` has named them.
        Stream streams[2];

        // The logcat copy of one line, written when it was asked for or when the line says something is
        // broken: an error is worth seeing without reproducing the run with a flag. The line goes as it
        // stands - logcat caps an entry itself - and nothing here shortens one first.
        void Logcat(int priority, const char* logcatTag, const std::string& line) {
            if (logcat || priority >= ANDROID_LOG_ERROR)
                __android_log_write(priority, logcatTag, line.c_str());
        }

        // The letter the file spells a priority with; logcat's own numbers are what both VMs send.
        const char* LetterFor(int priority) {
            switch (priority) {
                case ANDROID_LOG_VERBOSE: return "[V]";
                case ANDROID_LOG_DEBUG:   return "[D]";
                case ANDROID_LOG_WARN:    return "[W]";
                case ANDROID_LOG_ERROR:   return "[E]";
                default:                  return "[I]";
            }
        }

        // The length of the head arc writes in front of its own lines: `[I] `, `[W] `, `[E] `, `[D] `, `[V] `.
        constexpr size_t HEAD_LENGTH = 4;

        // The level such a head declares, or -1 when the line has none. Exactly one upper case letter and the
        // space that is part of the head: anything else - a bracket opened for another reason (`[Audio] ...`),
        // a lower case letter, no space - is not a level, and that line keeps the stream's level and its bytes.
        int HeadLevel(const std::string& line) {
            if (line.size() < HEAD_LENGTH || line[0] != '[' || line[2] != ']' || line[3] != ' ')
                return -1;
            switch (line[1]) {
                case 'V': return ANDROID_LOG_VERBOSE;
                case 'D': return ANDROID_LOG_DEBUG;
                case 'I': return ANDROID_LOG_INFO;
                case 'W': return ANDROID_LOG_WARN;
                case 'E': return ANDROID_LOG_ERROR;
                default:  return -1;
            }
        }

        // Writes a whole line, over as many calls as the kernel needs: a full disk, or a signal that arrived
        // before any of the line was written, makes `write` report less than it was given. The mutex is held
        // across all of them, so the remainder lands as the very next bytes of the file.
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
                // Nothing left to try: the file is full or gone, and half a line is all anyone was going to
                // get out of it.
                return;
            }
        }

        // Appends one whole line, newline included, under the file's lock. The string is built here rather
        // than written in pieces, so a reader never sees half of it, and the write happens under `fileMutex`
        // because an appending write being atomic is what one filesystem does, not what the standard
        // promises.
        void AppendToFile(const std::string& entry) {
            const std::string line = entry + '\n';
            std::lock_guard<std::mutex> guard(fileMutex);
            if (logFd < 0)
                return;
            WriteAll(logFd, line);
        }

        // Logs whatever has been collected as one line, if anything has. The caller holds the stream's mutex.
        void FlushLineLocked(Stream& stream) {
            if (stream.line.empty())
                return;
            // A captured line may carry its own level, and then that level is the line's and comes out of the
            // message, so the file carries one and not two. Without one the stream's level stands and the bytes
            // go as they came. The side is the game's either way: these bytes are what the JVM's streams printed.
            int priority = stream.priority;
            std::string message = stream.line;
            const int declared = HeadLevel(stream.line);
            if (declared >= 0 && &stream != &streams[1]) {
                priority = declared;
                message = stream.line.substr(HEAD_LENGTH);
            }

            const std::string head = std::string(LetterFor(priority)) + "[" + GAME_SIDE.letter + "] ";
            At(priority, GAME_SIDE.logcatTag, head + message);
            stream.line.clear();
        }

        // Adds one byte to the line being assembled, cutting it where a line is allowed to be cut.
        void AddByteLocked(Stream& stream, char c) {
            if (c == '\n') {
                FlushLineLocked(stream);
                return;
            }
            if (c == '\r')
                return;

            // Cut at a character boundary: logcat reads UTF-8, and a cut inside a sequence would arrive as two
            // broken characters instead of one line too long.
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

        // Recorded with the descriptor, never before it: the two are set together, so a path that is known is
        // a file that is open, and a file that was never opened is one the hooks recognise nothing of.
        logPath = path;

        logcat = wanted;

        // The two streams are given their priorities here: the write hooks can feed them before a descriptor
        // is ever captured. stdout is ordinary output; stderr is where a VM says something is wrong.
        streams[0].priority = ANDROID_LOG_INFO;
        streams[1].priority = ANDROID_LOG_ERROR;
        return true;
    }

    const std::string& Path() {
        return logPath;
    }

    void At(int priority, const char* logcatTag, const std::string& line) {
        // The line arrives finished - the file's text, head included - and logcat gets that same text.
        Logcat(priority, logcatTag, line);
        AppendToFile(line);
    }

        namespace {

        // How long a formatted message may be: the formatting buffer, not a destination's limit.
        constexpr size_t MAX_MESSAGE_LENGTH = 4096;

        // This library's own line, the way the file spells it: the level letter, this library's side, then the
        // tag of the subsystem that produced it - or nothing, when the line names no subsystem. Logcat gets
        // that same text.
        void NativeLine(int priority, const char* tag, const std::string& message) {
            const std::string brackets = tag == NO_TAG ? "" : std::string(" [") + tag + "]";
            At(priority, NATIVE_SIDE.logcatTag,
                    std::string(LetterFor(priority)) + "[" + NATIVE_SIDE.letter + "]" + brackets + " " + message);
        }

        std::string FormatMessage(const char* format, va_list args) {
            char buffer[MAX_MESSAGE_LENGTH];
            vsnprintf(buffer, sizeof(buffer), format, args);
            return std::string(buffer);
        }

        } // namespace

    void FeedStream(int streamIndex, const char* bytes, size_t length) {
        if (bytes == nullptr || streamIndex < 0 || streamIndex > 1)
            return;

        Stream& stream = streams[streamIndex];
        std::lock_guard<std::mutex> guard(stream.mutex);
        for (size_t i = 0; i < length; i++)
            AddByteLocked(stream, bytes[i]);
    }

    void Verbose(const char* tag, const std::string& message) { NativeLine(ANDROID_LOG_VERBOSE, tag, message); }
    void Debug(const char* tag, const std::string& message) { NativeLine(ANDROID_LOG_DEBUG, tag, message); }
    void Info(const char* tag, const std::string& message) { NativeLine(ANDROID_LOG_INFO, tag, message); }
    void Warn(const char* tag, const std::string& message) { NativeLine(ANDROID_LOG_WARN, tag, message); }
    void Error(const char* tag, const std::string& message) { NativeLine(ANDROID_LOG_ERROR, tag, message); }

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
