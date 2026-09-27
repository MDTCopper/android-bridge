package copper.bridge.util;

import copper.bridge.annotation.*;
import java.io.*;
import java.nio.charset.*;

/**
 * The bridge's logger, on both virtual machines. One file per run holds everything: the bridge's lines from both
 * VMs, whatever either VM writes to stdout or stderr, and the game's output. Which side writes it changes once,
 * when the native library takes it over, so each entry point sets the {@link LogBackend} its own side starts
 * with, and no line is forwarded to the other VM or queued. A line is a level, a side, a tag and a message, and
 * they stay apart: the tag is an argument like the level is, never text inside the message - and a line that
 * names no subsystem has none. The file spells them out as `[level][side][tag]`, or `[level][side]` without a
 * tag, and logcat gets that same text under the side's tag.
 */
public class Log {

    public enum Level {
        ERROR, WARN, INFO, DEBUG, VERBOSE
    }

    /**
     * The side of the process a line came from: the letter the file names it with, and the tag its lines carry
     * in Android's log. One value carries both, because the two are the same fact. The sides whose lines are
     * written in C++ - this library's and the game's - are declared there.
     */
    public enum Side {
        ART("A", "CopperBridgeArt"),
        JVM("J", "CopperBridgeJvm");

        private final String letter;
        private final String logcatTag;

        Side(String letter, String logcatTag) {
            this.letter = letter;
            this.logcatTag = logcatTag;
        }

        /** The letter the file names this side with. */
        public String letter() {
            return letter;
        }

        /** The tag this side's lines carry in Android's log. */
        public String logcatTag() {
            return logcatTag;
        }
    }

    private static Level level = Level.INFO;

    /**
     * Where this side's lines go, or {@code null} until an entry point sets one.
     *
     * <p>Both entry points set one before their first line: the backend a side starts with is that
     * side's own - ART writes the file and logcat itself until the library is loaded, the JVM hands
     * every line over - so there is nothing here to default to.</p>
     */
    private static LogBackend backend = null;

    // Read by the backends, which are what build a line: whether logcat was asked for, and the side
    // this VM's lines come from.
    static boolean logcat = false;
    static Side side = Side.ART;

    private static Writer fileWriter = null;
    private static String logFilePath = null;

    /** Sets the minimum log level. Messages below this level are suppressed. */
    public static void setLevel(Level level) {
        Log.level = level;
    }

    /** Chooses where this VM's lines go; see {@link LogBackend}. */
    public static void setBackend(LogBackend backend) {
        Log.backend = backend;
    }

    /**
     * Whether lines may also go to Android's log.
     *
     * <p>Off unless it was asked for on the command line: the file holds everything, and logcat is a
     * shared, rate limited buffer that a game's own output can fill by itself.</p>
     */
    public static void setLogcat(boolean enabled) {
        Log.logcat = enabled;
    }

    /**
     * Sets the side this VM's lines come from: the file's side letter and the tag logcat gets are both taken
     * from it. Each entry point sets its own before its first line; ART is the VM the process starts on, so it
     * is what a line carries until one does.
     */
    public static void setSide(Side side) {
        Log.side = side;
    }

    /**
     * Opens the log file for appending and remembers where it is, so the native side can be told
     * where the lines belong. Only the first call takes effect; later calls are ignored.
     *
     * <p>Appending, never truncating: the caller decides when a run gets a new file, and the side
     * that takes the file over afterwards must not lose what the side before it wrote.</p>
     */
    public static void setOutputFile(File file) {
        if (fileWriter != null)
            return;
        try {
            file.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(file, true);
            fileWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            logFilePath = file.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("failed to setup log output file: " + file.getPath(), e);
        }
    }

    /**
     * Gives up this class's own writer, keeping the path.
     *
     * <p>Called just before the native library is loaded: from its first instruction that side is the
     * writer of the file, and two writers that each kept their own offset would overwrite each
     * other's lines. The path stays, so a load that fails can reopen the file and say so.</p>
     */
    public static void closeOutputFile() {
        if (fileWriter == null)
            return;
        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception ignored) {
            // Nothing to report it through without reaching this writer again.
        }
        fileWriter = null;
    }

    /** The file this VM's lines belong in, or {@code null} while none was named. Read by native. */
    @UsedByNative(side = UsedByNative.Side.ART)
    @SuppressWarnings("unused")
    private static String logFilePath() {
        return logFilePath;
    }

    /** Whether logcat was asked for. Read by native, which owns both destinations. */
    @UsedByNative(side = UsedByNative.Side.ART)
    @SuppressWarnings("unused")
    private static boolean logcatEnabled() {
        return logcat;
    }

    @UsedByNative(side = UsedByNative.Side.ART)
    @SuppressWarnings("unused")
    private static int logLevel() {
        return level.ordinal();
    }

    /**
     * Logs a message at the given level, under the given tag.
     *
     * <p>The tag is a value here and not text: the backend builds the file's line from it, and logcat
     * gets that same line under the tag of the side that produced it. A {@code null} tag writes no tag at
     * all, which is what a line naming no subsystem uses - the side letter already says whose line it is.</p>
     */
    public static void log(Level level, String tag, String msg) {
        if (backend == null || level.ordinal() > Log.level.ordinal())
            return;

        backend.write(level, tag, msg);
    }

    /** Logs a message at the given level, with no tag. */
    public static void log(Level level, String msg) {
        log(level, null, msg);
    }

    /** Appends one line to the log file, while the Java side is the one writing it. */
    static void writeToFile(String line) {
        if (fileWriter == null)
            return;
        try {
            fileWriter.write(line + "\n");
            fileWriter.flush();
        } catch (Exception e) {
            // Not reported through this class: the call would reach the same writer again, and a
            // stream that always fails would recurse until the stack runs out.
        }
    }

    /** Logs a formatted message at the given level, under the given tag. */
    public static void log(Level level, String tag, String format, Object... args) {
        log(level, tag, String.format(format, args));
    }

    /** Logs the stack trace of a throwable at error level, with no tag. */
    public static void error(Throwable t) {
        error(null, t);
    }

    /** Logs the stack trace of a throwable at error level, under the given tag. */
    public static void error(String tag, Throwable t) {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        t.printStackTrace(pw);
        pw.flush();
        error(tag, writer.toString());
    }

    public static void error(String msg) {
        log(Level.ERROR, null, msg);
    }

    public static void error(String tag, String msg) {
        log(Level.ERROR, tag, msg);
    }

    public static void error(String tag, String format, Object... args) {
        log(Level.ERROR, tag, format, args);
    }

    public static void warn(String msg) {
        log(Level.WARN, null, msg);
    }

    public static void warn(String tag, String msg) {
        log(Level.WARN, tag, msg);
    }

    public static void warn(String tag, String format, Object... args) {
        log(Level.WARN, tag, format, args);
    }

    public static void info(String msg) {
        log(Level.INFO, null, msg);
    }

    public static void info(String tag, String msg) {
        log(Level.INFO, tag, msg);
    }

    public static void info(String tag, String format, Object... args) {
        log(Level.INFO, tag, format, args);
    }

    public static void debug(String msg) {
        log(Level.DEBUG, null, msg);
    }

    public static void debug(String tag, String msg) {
        log(Level.DEBUG, tag, msg);
    }

    public static void debug(String tag, String format, Object... args) {
        log(Level.DEBUG, tag, format, args);
    }

    public static void verbose(String msg) {
        log(Level.VERBOSE, null, msg);
    }

    public static void verbose(String tag, String msg) {
        log(Level.VERBOSE, tag, msg);
    }

    public static void verbose(String tag, String format, Object... args) {
        log(Level.VERBOSE, tag, format, args);
    }
}
