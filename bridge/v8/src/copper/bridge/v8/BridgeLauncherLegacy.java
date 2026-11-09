package copper.bridge.v8;

import copper.bridge.annotation.BridgeOverride;

import arc.*;
import arc.files.*;
import arc.func.*;

/**
 * The multi-file entry point of the pre-159 Platform. It lives in a class that never mentions
 * {@code FileChooserParams}, so an epoch without that class can still load the launcher.
 */
public class BridgeLauncherLegacy extends BridgeLauncher {

    /** The pre-159 multiple-file picker; the single-file entry points moved to {@code FileChooserParams}.
     */
    @BridgeOverride
    public void showMultiFileChooser(Cons<Fi> cons, String... extensions) {
        showFileChooser(true, "@open", cons, extensions);
    }

    private void showFileChooser(boolean open, String title, Cons<Fi> cons, String... extensions) {
        String[] filter = extensions == null || extensions.length == 0 ? new String[] {""} : extensions;

        requestFiles(open, false, title, null, filter, paths -> {
            for (String path : paths) {
                if (path == null || path.isEmpty())
                    continue;
                cons.get(open ? Core.files.absolute(path) : new UriFi(path, "export." + filter[0]));
            }
        });
    }
}
