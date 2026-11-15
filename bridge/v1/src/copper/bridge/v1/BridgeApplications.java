package copper.bridge.v1;

/**
 * Picks the application whose shape matches the game that is running. The floor still has
 * {@code Application.getClipboard}, the ceiling replaced it with the text methods, so the two shapes cannot share
 * one concrete class. The choice is probed, not versioned: the version table already owns the range.
 */
public final class BridgeApplications {
    private static final String CLIPBOARD_TYPE = "io.anuke.arc.util.Clipboard";
    /** Reached by name, never as a type: this class is loaded on the ceiling too, which has no Clipboard. */
    private static final String LEGACY_APPLICATION = "copper.bridge.v1.BridgeApplicationLegacy";

    private BridgeApplications() {
    }

    public static BridgeApplication create() {
        if (hasClipboard())
            return legacy();
        return new BridgeApplicationCurrent();
    }

    static boolean hasClipboard() {
        try {
            Class.forName(CLIPBOARD_TYPE);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static BridgeApplication legacy() {
        try {
            return (BridgeApplication)Class.forName(LEGACY_APPLICATION)
                    .getConstructor()
                    .newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("the game declares " + CLIPBOARD_TYPE + " but " + LEGACY_APPLICATION
                    + " could not be created", e);
        }
    }
}
