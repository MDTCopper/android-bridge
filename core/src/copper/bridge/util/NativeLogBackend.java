package copper.bridge.util;

import copper.bridge.annotation.*;

/**
 * The backend of a side whose log file belongs to the native library: both VMs reach this one.
 */
public class NativeLogBackend extends LogBackend {

    @Override
    public void write(Log.Level level, String tag, String message) {
        String line = fileLine(level, side(), tag, message);
        try {
            logLine(level.ordinal(), side().logcatTag(), line);
        } catch (Throwable ignored) {
            // The library was never bound: the stream the native capture would have taken the line from still has it.
            System.out.println(line);
        }
    }

    /**
     * Hands one finished line to the native side, which appends it to the log file and, when logcat was asked for,
     * writes that same text to Android's log. This is the one thing a Java backend cannot do for itself:
     * {@code android.util.Log} exists on ART only, and the file belongs to the native side once loaded.
     */
    @Native("jni::Log::LogLine")
    private static native void logLine(int level, String logcatTag, String line);
}
