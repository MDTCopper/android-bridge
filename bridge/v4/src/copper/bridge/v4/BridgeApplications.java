package copper.bridge.v4;

/** Picks the application whose function interfaces match the running build. */
public final class BridgeApplications {
    private static final String CONSUMER = "io/anuke/arc/func/Cons";
    private static final String LEGACY = "copper.bridge.v4.BridgeApplicationLegacy";

    private BridgeApplications() {
    }

    public static BridgeApplication create() {
        if (!hasCons())
            return new BridgeApplication();
        try {
            return (BridgeApplication)Class.forName(LEGACY).getConstructor().newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("the game declares " + CONSUMER + " but " + LEGACY + " could not be created",
                    e);
        }
    }

    static boolean hasCons() {
        try {
            Class.forName(CONSUMER);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
