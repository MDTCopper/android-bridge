package copper.bridge.v7;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.struct.*;

import copper.bridge.gen.*;
import copper.bridge.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.net.*;
import mindustry.ui.FileChooser.*;

/**
 * The game launcher for this branch: the game's own {@link ClientLauncher}, so start-up order, loading screen and
 * module list stay what the game expects, and only the platform answers differ.
 */
public class BridgeLauncher extends ClientLauncher implements Platform {

    public BridgeLauncher() {
        // Installs the game's crash reporting, which the stock launcher installs for itself: into the game's
        // {@code crashes} folder, and the method swallows its own failures, so nothing can escape here. The handler
        // already in place is kept and called after the report: the host app owns the activity and the process.
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            CrashHandler.log(error);
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

    /** Sends one file-picker request and hands the raw answer to {@code params}. */
    private void requestFiles(boolean open, boolean allowMultiple, String title, String fileName,
                              String[] extensions, Cons<String[]> onPicked) {
        JvmCall.showFileChooser(open, allowMultiple, title, fileName, extensions)
                .onResult(paths -> {
                    if (paths == null)
                        return;
                    onPicked.get(paths);
                })
                .onError(message -> Log.error("file chooser failed: " + message));
    }

    /**
     * The whole picker in this epoch is {@code params}: it already carries the direction, the multi-select flag, the
     * extensions, the default name and the caller's handler, and
     * {@link FileChooserParams#handleChooseResult(Fi...)} decides between the single and the multiple one, so this
     * class never has to know which kind of request it is answering.
     */
    @Override
    public void showFileChooser(FileChooserParams params) {
        String[] filter = params.extensions == null || params.extensions.length == 0
                ? new String[] {""} : params.extensions;

        requestFiles(params.open, params.allowMultiple, params.title, params.fileName, filter, paths -> {
            if (paths.length == 0)
                return;

            // an open request answers with real paths and a save request with document URIs;
            // a dismissed picker answers with an empty array, which the early return above
            // turns into "no file chosen" without bothering the caller
            Seq<Fi> picked = new Seq<>();
            for (String path : paths) {
                if (path == null || path.isEmpty())
                    continue;
                picked.add(params.open
                        ? Core.files.absolute(path)
                        : new UriFi(path, params.fileName == null ? "export" : params.fileName));
            }
            params.handleChooseResult(picked.toArray(Fi.class));
        });
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
