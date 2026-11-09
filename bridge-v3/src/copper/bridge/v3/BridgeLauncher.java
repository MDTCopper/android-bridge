package copper.bridge.v3;

import io.anuke.arc.*;
import io.anuke.arc.files.*;
import io.anuke.arc.func.*;

import copper.bridge.gen.*;
import copper.bridge.util.*;
import io.anuke.mindustry.*;
import io.anuke.mindustry.core.*;
import io.anuke.mindustry.game.*;
import io.anuke.mindustry.net.*;

/**
 * The game launcher for this branch: the game's own {@link ClientLauncher}, so start-up order, loading screen and
 * module list stay what the game expects, and only the platform answers differ. The 6 line asks the platform for a
 * file through the three-entry chooser, and for several files through the multi-entry one.
 */
public class BridgeLauncher extends ClientLauncher implements Platform {

    public BridgeLauncher() {
        // Installs the game's crash reporting, which the stock launcher installs for itself. The handler already in
        // place is kept and called after the report: the host app owns the activity and the process.
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            handleCrash(error);
            if (handler != null) {
                handler.uncaughtException(thread, error);
            } else {
                io.anuke.arc.util.Log.err(error);
                System.exit(1);
            }
        });

        // break force landscape set by some launchers
        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (!Core.settings.getBool("landscape"))
                endForceLandscape();
        });
    }

    /** Writes the crash report through the game's own sender; {@code log} only exists on the newer end. */
    private void handleCrash(Throwable cause) {
        try {
            CrashSender.class.getMethod("log", Throwable.class).invoke(null, cause);
        } catch (Throwable ignored) {
            io.anuke.arc.util.Log.err(cause);
        }
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
    public void shareFile(FileHandle file) {
    }

    /**
     * Opens the system file picker in place of the in-game browser the default shows. The answer arrives later on the
     * request queue, so only the caller's listener crosses to ART: an open request yields paths the game can read at
     * once (ART has already copied the documents into the bridge cache), a save request the URIs the game writes
     * through {@link UriFi} when it is ready.
     */
    @Override
    public void showFileChooser(boolean open, String title, Cons<FileHandle> cons) {
        pick(open, title, new String[] {""}, cons);
    }

    /** The same picker with a file-type filter. The interface gained this method after the range's floor, so it
     *  carries no {@code @Override}. */
    public void showMultiFileChooser(Cons<FileHandle> cons, String... extensions) {
        pick(true, "@open", extensions, cons);
    }

    private void pick(boolean open, String title, String[] extensions, Cons<FileHandle> cons) {
        String[] filter = extensions == null || extensions.length == 0 ? new String[] {""} : extensions;

        JvmCall.showFileChooser(open, false, title, null, filter)
                .onResult(paths -> {
                    if (paths == null)
                        return;
                    for (String path : paths) {
                        if (path == null || path.isEmpty())
                            continue;
                        cons.get(open ? Core.files.absolute(path) : new UriFi(path, "export." + filter[0]));
                    }
                })
                .onError(message -> Log.error("file chooser failed: " + message));
    }
}