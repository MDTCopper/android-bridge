package copper.bridge.art;

import android.content.*;

import copper.bridge.annotation.*;
import copper.bridge.util.*;

/**
 * Hands a URI or a folder over to whatever the system has registered for it.
 *
 * <p>Both directions start another app, which is the activity's to do, but nothing else about the activity is
 * involved, so this is not a method of it. Posted rather than called directly even though they only want a
 * value back: starting an activity belongs to the main thread.</p>
 */
public class UriLauncher {
    private final Context context;

    public UriLauncher(Context context) {
        this.context = context;
    }

    /**
     * Opens a URI with whatever the system has registered for it. The context is the activity, so
     * {@code startActivity} starts the new activity in this task and no {@code NEW_TASK} flag is needed.
     */
    @ArtPostHandler
    public boolean openUri(String uri) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)));
            return true;
        } catch (Throwable e) {
            Log.warn("cannot open " + uri + ": " + e);
            return false;
        }
    }

    /** Opens a folder with the system file manager. Posted for the same reason as {@link #openUri}. */
    @ArtPostHandler
    public boolean openFolder(String folder) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse("file://" + folder), "resource/folder");
            if (intent.resolveActivityInfo(context.getPackageManager(), 0) == null)
                return false;
            context.startActivity(intent);
            return true;
        } catch (Throwable e) {
            Log.warn("cannot open " + folder + ": " + e);
            return false;
        }
    }
}
