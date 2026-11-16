package copper.bridge.v1;

/**
 * The surface ART handed over, as the JVM side sees it. One process has one window, so the state is this class's
 * own and every reader asks it directly. Writers run on the thread that owns the game, so a reader there never
 * has to synchronize with them; the lock is for the one reader on a different thread, the wait before the loop
 * starts.
 */
public class Surface {

    private static final Object lock = new Object();

    private static long window = 0;
    private static int width = 0;
    private static int height = 0;
    private static boolean ready = false;

    private Surface() {
    }

    public static void created(long window, int width, int height) {
        synchronized (lock) {
            Surface.window = window;
            Surface.width = width;
            Surface.height = height;
            Surface.ready = true;
        }
    }

    public static void resized(int width, int height) {
        synchronized (lock) {
            Surface.width = width;
            Surface.height = height;
        }
    }

    public static void destroyed() {
        synchronized (lock) {
            window = 0;
            ready = false;
        }
    }

    public static long window() {
        synchronized (lock) {
            return window;
        }
    }

    public static int width() {
        synchronized (lock) {
            return width;
        }
    }

    public static int height() {
        synchronized (lock) {
            return height;
        }
    }

    public static boolean ready() {
        synchronized (lock) {
            return ready;
        }
    }
}
