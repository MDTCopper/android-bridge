package copper.bridge.v1;

import arc.*;
import arc.files.*;
import arc.func.*;

import copper.bridge.gen.*;
import copper.bridge.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.*;

/**
 * The game launcher for this branch, minus the file chooser: the game's own {@link ClientLauncher}, so start-up
 * order, loading screen and module list stay what the game expects, and only the platform answers differ. The
 * chooser is absent because its shape changed inside the range - 159 replaced four entry points with one
 * {@code FileChooserParams} request - hence the {@link BridgeLauncherLegacy} / {@link BridgeLauncherParams} split.
 */
public abstract class BridgeLauncher extends ClientLauncher implements Platform {

    public BridgeLauncher() {
        // Installs the game's crash reporting, which the stock launcher installs for itself. The handler already in
        // place is kept and called after the report: the host app owns the activity and the process.
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            handleCrash(error);
            if (handler != null) {
                handler.uncaughtException(thread, error);
            } else {
                arc.util.Log.err(error);
                System.exit(1);
            }
        });

        // break force landscape set by some launchers
        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (!Core.settings.getBool("landscape"))
                endForceLandscape();
        });
    }

    /**
     * Writes the crash report through whichever type the running game declares: 146 has {@code CrashSender}, whose
     * {@code log} became {@code CrashHandler}'s before 147. Reached by name because a plain call would put one of the
     * two names into this class's constant pool and stop every epoch lacking it from loading the launcher at all.
     */
    private void handleCrash(Throwable cause) {
        try {
            Class.forName("mindustry.net.CrashHandler")
                    .getDeclaredMethod("log", Throwable.class)
                    .invoke(null, cause);
        } catch (Throwable e) {
            try {
                Class.forName("mindustry.net.CrashSender")
                        .getDeclaredMethod("log", Throwable.class)
                        .invoke(null, cause);
            } catch (Throwable E) {
                arc.util.Log.err(cause);
                System.exit(1);
            }
        }
    }

    /** Sends one file-picker request and hands the raw answer to the shape-specific caller; the error line lives
     *  here so the two chooser shapes cannot drift. */
    protected final void requestFiles(boolean open, boolean allowMultiple, String title, String fileName,
                                      String[] extensions, Cons<String[]> onPicked) {
        JvmCall.showFileChooser(open, allowMultiple, title, fileName, extensions)
                .onResult(paths -> {
                    if (paths == null)
                        return;
                    onPicked.get(paths);
                })
                .onError(message -> Log.error("file chooser failed: " + message));
    }

    @Override
    public void hide() {
        JvmCall.hide();
    }

    /** Pins the game to landscape, or releases it to the user's rotation setting; released, the game can end up
     *  portrait. */
    @Override
    public void beginForceLandscape() {
        JvmCall.forceLandscape(true);
    }

    @Override
    public void endForceLandscape() {
        JvmCall.forceLandscape(false);
    }

    /** Left empty on purpose: a share sheet would need a {@code FileProvider} in the host app's manifest. */
    @Override
    public void shareFile(Fi file) {
    }
}
