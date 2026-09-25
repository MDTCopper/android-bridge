package copper.bridge.util;

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
            Log.logLine(priority(level), side().logcatTag(), line);
        } catch (Throwable ignored) {
            // The library was never bound, or is gone; the stream the native capture would have taken
            // the line from still has it, and that stream is this side's.
            System.out.println(line);
        }
    }
}
