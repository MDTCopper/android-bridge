package copper.bridge.art;

import copper.bridge.util.Log;
import copper.bridge.util.LogBackend;

/**
 * The backend of the ART side before the native library is loaded: this side owns the log file until then, so
 * it writes the file itself, and Android's log is reached with {@code android.util.Log.println} - the one
 * call only this VM can make.
 */
class AndroidLogBackend extends LogBackend {

    @Override
    public void write(Log.Level level, String tag, String message) {
        String line = fileLine(level, side(), tag, message);
        appendToFile(line);
        // An error reaches logcat even when logcat was not asked for, so a crash is visible without the file.
        if (logcatWanted() || level == Log.Level.ERROR)
            android.util.Log.println(priority(level), side().logcatTag(), line);
    }
}
