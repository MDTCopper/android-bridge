package copper.bridge.util;

/**
 * Where one side's log lines go.
 *
 * <p>The logger hands a line over as its three fields and the backend is what writes it: the text
 * the file holds is built here, the same way for every side, and where that text goes is the
 * backend's own. Which backend is in use changes once, when the native library takes the log file
 * over, so the two sides share the vocabulary of a line and nothing else.</p>
 */
public abstract class LogBackend {

    /**
     * The priorities {@code android.util.Log} defines.
     *
     * <p>Written out rather than looked up: this class is shared by two VMs, one of which has no
     * Android classes at all, and the numbers are part of logcat's interface.</p>
     */
    private static final int PRIORITY_VERBOSE = 2;
    private static final int PRIORITY_DEBUG = 3;
    private static final int PRIORITY_INFO = 4;
    private static final int PRIORITY_WARN = 5;
    private static final int PRIORITY_ERROR = 6;

    /** Writes one line: its level, its tag - {@code null} for none - and the message. */
    public abstract void write(Log.Level level, String tag, String message);

    /**
     * The text the file holds for a line: the level letter, the side letter, the tag, then the message, the
     * brackets flush against each other. A {@code null} tag writes none, leaving the level and the side.
     *
     * <p>The side comes from the same value logcat's tag does, so the two can never name different sides.</p>
     */
    protected static String fileLine(Log.Level level, Log.Side side, String tag, String message) {
        return letter(level) + "[" + side.letter() + "]" + (tag == null ? "" : " [" + tag + "]") + " " + message;
    }

    /** The logcat priority of a level; logcat's own numbers, which the native side takes as they are. */
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

    /** Appends one line to the log file, while the Java side is the one writing it. */
    protected static void appendToFile(String line) {
        Log.writeToFile(line);
    }

    /** Whether logcat was asked for. */
    protected static boolean logcatWanted() {
        return Log.logcat;
    }

    /** The side this VM's lines come from: the file's side letter and logcat's tag are both taken from it. */
    protected static Log.Side side() {
        return Log.side;
    }

    /** The letter the file spells a level with. */
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
