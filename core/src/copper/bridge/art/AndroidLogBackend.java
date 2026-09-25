package copper.bridge.art;

import copper.bridge.util.Log;
import copper.bridge.util.LogBackend;

/**
 * The backend of the ART side before the native library is loaded.
 *
 * <p>This side owns the log file until then, so it writes the file itself, and Android's log is
 * reached with {@code android.util.Log.println} - the one call only this VM can make. Both
 * destinations get the same text: the level is logcat's own field, and the tag names this side.</p>
 */
class AndroidLogBackend extends LogBackend {

    @Override
    public void write(Log.Level level, String tag, String message) {
        String line = fileLine(level, side(), tag, message);
        appendToFile(line);
        if (logcatWanted() || level == Log.Level.ERROR)
            android.util.Log.println(priority(level), side().logcatTag(), line);
    }
}
