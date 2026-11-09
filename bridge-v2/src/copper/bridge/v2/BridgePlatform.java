package copper.bridge.v2;

import io.anuke.arc.*;
import io.anuke.arc.files.*;
import io.anuke.arc.function.*;
import io.anuke.mindustry.core.Platform;

import copper.bridge.gen.*;
import copper.bridge.util.*;

/**
 * The game's platform hooks for the pre-ClientLauncher 6 line. This era has no {@code ClientLauncher}: the game
 * itself is the {@code ApplicationCore} listener, and everything a platform answers lives on the abstract
 * {@link Platform} whose {@code instance} this constructor installs.
 */
public class BridgePlatform extends Platform {

    public BridgePlatform() {
        Platform.instance = this;
    }

    @Override
    public void hide() {
        JvmCall.hide();
    }

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
     * The era's chooser carries a description and a predicate instead of an extension list; the predicate is
     * applied to the paths the picker returns, since ART cannot turn it back into a MIME filter.
     */
    @Override
    public void showFileChooser(String text, String content, Consumer<FileHandle> cons, boolean open,
                                Predicate<String> filetype) {
        JvmCall.showFileChooser(open, false, text, null, new String[] {""})
                .onResult(paths -> {
                    if (paths == null)
                        return;
                    for (String path : paths) {
                        if (path == null || path.isEmpty())
                            continue;
                        if (filetype != null && !filetype.test(path))
                            continue;
                        cons.accept(open ? Core.files.absolute(path) : new UriFi(path, "export"));
                    }
                })
                .onError(message -> Log.error("file chooser failed: " + message));
    }
}
