package copper.bridge.util;

/**
 * Where one side's log lines go: the backend builds the text the file holds from the line's three fields.
 */
public abstract class LogBackend {

    /**
     * The priorities {@code android.util.Log} defines. Written out rather than looked up: this class is shared
     * by two VMs, one of which has no Android classes at all, and the numbers are part of logcat's interface.
     */
    private static final int PRIORITY_VERBOSE = 2;
    private static final int PRIORITY_DEBUG = 3;
    private static final int PRIORITY_INFO = 4;
    private static final int PRIORITY_WARN = 5;
    private static final int PRIORITY_ERROR = 6;

    /** Writes one line: its level, its tag - {@code null} for none - and the message. */
    public abstract void write(Log.Level level, String tag, String message);

    /** The text the file holds: the level letter, the side letter, the tag, then the message, brackets flush. A
     *  {@code null} tag writes none. */
    protected static String fileLine(Log.Level level, Log.Side side, String tag, String message) {
        return letter(level) + "[" + side.letter() + "]" + (tag == null ? "" : " [" + tag + "]") + " " + message;
    }

    protected static int priority(Log.Level level) {
        switch (level) {
            case ERROR:   return PRIORITY_ERROR;
            case WARN:    return PRIORITY_WARN;
            case INFO:    return PRIORITY_INFO;
            case DEBUG:   return PRIORITY_DEBUG;
            case VERBOSE: return PRIORITY_VERBOSE;
            default:      return PRIORITY_INFO;
        }
    }

    protected static void appendToFile(String line) {
        Log.writeToFile(line);
    }

    protected static boolean logcatWanted() {
        return Log.logcat;
    }

    protected static Log.Side side() {
        return Log.side;
    }

    private static String letter(Log.Level level) {
        switch (level) {
            case INFO:    return "[I]";
            case WARN:    return "[W]";
            case ERROR:   return "[E]";
            case DEBUG:   return "[D]";
            case VERBOSE: return "[V]";
            default:      return "";
        }
    }
}
