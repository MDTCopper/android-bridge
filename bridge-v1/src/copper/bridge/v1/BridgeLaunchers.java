package copper.bridge.v1;

/**
 * Picks the launcher whose file chooser matches the game that is running. The choice is probed, not versioned: this
 * jar declares a single branch covering everything from 146.0 up, so it cannot answer "which file-chooser shape",
 * and a threshold hardcoded here would be a second, invisible copy of a boundary the version table owns.
 */
public final class BridgeLaunchers {
    private static final String PARAMS_TYPE = "mindustry.ui.FileChooser$FileChooserParams";
    /** The launcher that answers that request. Reached by name, never as a type. */
    private static final String PARAMS_LAUNCHER = "copper.bridge.v1.BridgeLauncherParams";

    private BridgeLaunchers() {
    }

    public static BridgeLauncher create() {
        if (hasFileChooserParams())
            return params();
        return new BridgeLauncherLegacy();
    }

    /** Whether the game declares the single-request file chooser. {@code Throwable} on purpose: a class that is
     *  present but cannot be initialised raises {@code NoClassDefFoundError}, not
     *  {@code ClassNotFoundException}. */
    static boolean hasFileChooserParams() {
        try {
            Class.forName(PARAMS_TYPE);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Creates the single-request launcher by name, not by type: naming the class here would put it in this class's
     * constant pool, and this class is loaded on every epoch - including the ones whose game has no
     * {@code FileChooserParams} for that launcher's method descriptor to resolve against.
     */
    private static BridgeLauncher params() {
        try {
            return (BridgeLauncher)Class.forName(PARAMS_LAUNCHER)
                    .getConstructor()
                    .newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("the game declares " + PARAMS_TYPE + " but " + PARAMS_LAUNCHER
                    + " could not be created", e);
        }
    }
}
