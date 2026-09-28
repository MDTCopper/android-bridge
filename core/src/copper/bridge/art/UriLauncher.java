package copper.bridge.art;

import android.content.*;

import copper.bridge.annotation.*;
import copper.bridge.util.*;

/**
 * Hands a URI or a folder over to whatever the system has registered for it. Posted rather than called
 * directly even though they only want a value back: starting an activity belongs to the main thread.
 */
public class UriLauncher {
    private final Context context;

    public UriLauncher(Context context) {
        this.context = context;
    }

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
