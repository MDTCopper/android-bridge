package copper.bridge.jvm;

/**
 * The surface ART handed over, as the JVM side sees it.
 *
 * <p>One process has one window, so there is nothing to construct and nothing to hand around: the state is this
 * class's own and every reader asks it directly. The writers are called from the event handlers, which run on the
 * thread that owns the game, so a reader there never has to synchronize with them; the lock is for the one reader
 * on a different thread, the wait before the loop starts. It exposes no game types, so a branch can be compiled
 * against it without dragging the core into a specific game version.</p>
 */
public class Surface {

    private static final Object lock = new Object();

    private static long window = 0;
    private static int width = 0;
    private static int height = 0;
    private static boolean ready = false;

    private Surface() {
    }

    /** Records a surface handed over by ART. */
    public static void created(long window, int width, int height) {
        synchronized (lock) {
            Surface.window = window;
            Surface.width = width;
            Surface.height = height;
            Surface.ready = true;
        }
    }

    /** Records a new surface size. */
    public static void resized(int width, int height) {
        synchronized (lock) {
            Surface.width = width;
            Surface.height = height;
        }
    }

    /** Records that the surface went away. */
    public static void destroyed() {
        synchronized (lock) {
            window = 0;
            ready = false;
        }
    }

    /** The current native window pointer, or 0 when there is no surface. */
    public static long window() {
        synchronized (lock) {
            return window;
        }
    }

    /** The current surface width. */
    public static int width() {
        synchronized (lock) {
            return width;
        }
    }

    /** The current surface height. */
    public static int height() {
        synchronized (lock) {
            return height;
        }
    }

    /** Whether a surface is currently available. */
    public static boolean ready() {
        synchronized (lock) {
            return ready;
        }
    }
}
