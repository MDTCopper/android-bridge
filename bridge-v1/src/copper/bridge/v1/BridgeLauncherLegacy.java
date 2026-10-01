package copper.bridge.v1;

import arc.*;
import arc.files.*;
import arc.func.*;

/**
 * The file chooser as every epoch before 159 declared it: four separate entry points for open, save, multiple
 * and native. Living in a class that never mentions {@code FileChooserParams} is what keeps those epochs
 * loadable.
 */
@SuppressWarnings("unused")
public class BridgeLauncherLegacy extends BridgeLauncher {

    /**
     * Opens the system file picker. The answer arrives later on the request queue, so only the caller's listener
     * crosses to ART: an open request yields paths the game can read at once (ART has already copied the documents
     * into the bridge cache), a save request the URIs the game writes through {@link UriFi} when it is ready.
     */
    public void showFileChooser(boolean open, String title, String extension, Cons<Fi> cons) {
        showFileChooser(open, title, cons, extension);
    }

    public void showMultiFileChooser(Cons<Fi> cons, String... extensions) {
        showFileChooser(true, "@open", cons, extensions);
    }

    /**
     * Redirected to the bridge picker: the base implementation would look for a native dialog library that only
     * ships with the desktop build.
     */
    public void showNativeFileChooser(boolean open, String title, Cons<Fi> cons, String... extensions) {
        showFileChooser(open, title, cons, extensions);
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
