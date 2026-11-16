package copper.bridge.v4;

/** Picks the launcher whose Platform and Events shapes match the running build. */
public final class BridgeLaunchers {
    private static final String LEGACY = "copper.bridge.v4.BridgeLauncherLegacy";

    private BridgeLaunchers() {
    }

    public static BridgeLauncher create() {
        if (!BridgeApplications.hasCons())
            return new BridgeLauncher();
        try {
            return (BridgeLauncher)Class.forName(LEGACY).getConstructor().newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("the game declares io/anuke/arc/function/Cons but " + LEGACY
                    + " could not be created", e);
        }
    }
}
