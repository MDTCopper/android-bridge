package copper.bridge.util;

import copper.bridge.annotation.*;

/**
 * The backend of a side whose log file belongs to the native library.
 *
 * <p>Both VMs reach this one: ART once the library is loaded and takes the file over, and the JVM
 * from its first line. The line is handed over whole - the native side appends it to the file and
 * writes that same text to Android's log - so nothing about a destination is decided here.</p>
 */
public class NativeLogBackend extends LogBackend {

    @Override
    public void write(Log.Level level, String tag, String message) {
        String line = fileLine(level, side(), tag, message);
        try {
            logLine(level.ordinal(), side().logcatTag(), line);
        } catch (Throwable ignored) {
            // The library was never bound, or is gone; the stream the native capture would have taken
            // the line from still has it, and that stream is this side's.
            System.out.println(line);
        }
    }

    /**
     * Hands one finished line to the native side, which appends it to the log file and, when logcat
     * was asked for, writes that same text to Android's log.
     *
     * <p>This is the one thing a Java backend cannot do for itself: {@code android.util.Log} exists
     * on ART only, and the file belongs to the native side once the library is loaded - so a line
     * produced on a side that has it loaded goes through here, whether that side is ART or the JVM.
     * The line is already the file's text, head included, and the tag names this side.</p>
     */
    @Native("jni::Log::LogLine")
    private static native void logLine(int level, String logcatTag, String line);
}
