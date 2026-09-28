package copper.bridge.util;

import copper.bridge.annotation.*;
import java.io.*;
import java.nio.charset.*;

/**
 * The bridge's logger on both virtual machines: one file per run holds both VMs' lines, whatever either writes to
 * stdout or stderr, and the game's output. A line is {@code [level][side][tag]}; logcat gets that same text.
 */
public class Log {

    public enum Level {
        ERROR, WARN, INFO, DEBUG, VERBOSE
    }

    /** The side a line came from: the letter the file names it with and the tag its lines carry in Android's log. */
    public enum Side {
        ART("A", "CopperBridgeArt"),
        JVM("J", "CopperBridgeJvm");

        private final String letter;
        private final String logcatTag;

        Side(String letter, String logcatTag) {
            this.letter = letter;
            this.logcatTag = logcatTag;
        }

        public String letter() {
            return letter;
        }

        public String logcatTag() {
            return logcatTag;
        }
    }

    private static Level level = Level.INFO;

    /** Where this side's lines go, or {@code null} until an entry point sets one. */
    private static LogBackend backend = null;

    static boolean logcat = false;
    static Side side = Side.ART;

    private static Writer fileWriter = null;
    private static String logFilePath = null;

    public static void setLevel(Level level) {
        Log.level = level;
    }

    public static void setBackend(LogBackend backend) {
        Log.backend = backend;
    }

    /** Off unless it was asked for: logcat is a shared, rate limited buffer the game's own output can fill by itself. */
    public static void setLogcat(boolean enabled) {
        Log.logcat = enabled;
    }

    public static void setSide(Side side) {
        Log.side = side;
    }

    /** Opens the log file for appending; only the first call takes effect, and never truncating. */
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

    /** Called just before the native library loads: two writers keeping their own offset would overwrite each other. */
    public static void closeOutputFile() {
        if (fileWriter == null)
            return;
        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (Exception ignored) {
        }
        fileWriter = null;
    }

    /** The file this VM's lines belong in, or {@code null} while none was named. Read by native. */
    @UsedByNative(side = UsedByNative.Side.ART)
    @SuppressWarnings("unused")
    private static String logFilePath() {
        return logFilePath;
    }

    @UsedByNative(side = UsedByNative.Side.ART)
    @SuppressWarnings("unused")
    private static boolean logcatEnabled() {
        return logcat;
    }

    /** The level this side writes at, as {@link Level#ordinal()}: native's level enum is in that same order. */
    @UsedByNative(side = UsedByNative.Side.ART)
    @SuppressWarnings("unused")
    private static int logLevel() {
        return level.ordinal();
    }

    /** The tag is a value and not text: the backend builds the line from it. A {@code null} tag writes none. */
    public static void log(Level level, String tag, String msg) {
        if (backend == null || level.ordinal() > Log.level.ordinal())
            return;

        backend.write(level, tag, msg);
    }

    public static void log(Level level, String msg) {
        log(level, null, msg);
    }

    static void writeToFile(String line) {
        if (fileWriter == null)
            return;
        try {
            fileWriter.write(line + "\n");
            fileWriter.flush();
        } catch (Exception e) {
            // not reported through this class: the call would reach the same writer again and recurse
        }
    }

    //region leveled convenience helpers
    public static void log(Level level, String tag, String format, Object... args) {
        log(level, tag, String.format(format, args));
    }

    public static void error(Throwable t) {
        error(null, t);
    }

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
    //endregion
}
