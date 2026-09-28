package copper.bridge.v1;

import arc.*;
import arc.files.*;
import arc.struct.*;
import mindustry.ui.FileChooser.*;

/**
 * The file chooser as Mindustry 159 and later declare it: one {@code FileChooserParams} request object
 * instead of four entry points.
 */
public class BridgeLauncherParams extends BridgeLauncher {

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
}
