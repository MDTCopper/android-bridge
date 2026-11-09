package copper.bridge.v5;

import java.util.*;
import java.util.concurrent.*;

/**
 * The music handles whose completion has to reach a listener. The music object lives in the branch, which the event
 * cannot see, so the handle is what crosses: the branch registers here, and its event forwards to it.
 */
public final class MusicListeners {
    private static final Map<Integer, Runnable> listeners = new ConcurrentHashMap<>();

    private MusicListeners() {
    }

    public static void put(int music, Runnable listener) {
        listeners.put(music, listener);
    }

    public static void remove(int music) {
        listeners.remove(music);
    }

    /** A track ended: the listener stays, because a music object plays more than once. */
    public static void complete(int music) {
        Runnable listener = listeners.get(music);
        if (listener != null)
            listener.run();
    }
}
