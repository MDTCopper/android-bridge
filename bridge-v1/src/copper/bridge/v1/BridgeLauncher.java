package copper.bridge.v1;

import arc.files.*;
import arc.func.*;

import copper.bridge.gen.*;
import copper.bridge.util.*;
import mindustry.*;
import mindustry.core.*;

/**
 * The game launcher for this branch, minus the file chooser: it is the game's own {@link ClientLauncher}, so
 * start-up order, loading screen and module list stay what the game expects, and only the platform answers differ -
 * each a request to ART, because the JVM side owns no Android object. The chooser is absent because its shape
 * changed inside the range: 159 replaced four entry points with one {@code FileChooserParams} request, and a class
 * the older half must load cannot name that type - hence the {@link BridgeLauncherLegacy} /
 * {@link BridgeLauncherParams} split, picked by {@link BridgeLaunchers} at run time.
 */
public abstract class BridgeLauncher extends ClientLauncher implements Platform{
    /**
     * Sends one file-picker request and hands the raw answer to the shape-specific caller: only the
     * request and the failure policy are shared, because the two chooser shapes disagree about what an
     * answer means and how many files it may return. The error line lives here so they cannot drift.
     */
    protected final void requestFiles(boolean open, boolean allowMultiple, String title, String fileName,
                                      String[] extensions, Cons<String[]> onPicked){
        JvmCall.showFileChooser(open, allowMultiple, title, fileName, extensions)
                .onResult(paths -> {
                    if(paths == null)
                        return;
                    onPicked.get(paths);
                })
                .onError(message -> Log.error("file chooser failed: " + message));
    }

    /** Moves the task to the background; the activity stays alive so the game can resume. */
    @Override
    public void hide(){
        JvmCall.hide();
    }

    /**
     * Pins the game to landscape, or releases it back to the user's own rotation setting. The call has
     * to reach the activity instance, because {@code setRequestedOrientation} decides the orientation
     * while the task runs, whatever the host declared; released, the game follows the user's rotation
     * preference and can end up portrait.
     */
    @Override
    public void beginForceLandscape(){
        JvmCall.orientation(true);
    }

    @Override
    public void endForceLandscape(){
        JvmCall.orientation(false);
    }

    /**
     * Left empty on purpose: every call site is behind {@code if(ios)}, and a share sheet would need a
     * {@code FileProvider} in the host app's manifest, which this bridge does not own and cannot
     * declare.
     */
    @Override
    public void shareFile(Fi file){
    }
}
