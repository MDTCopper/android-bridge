package copper.bridge.art;

import android.content.*;

import copper.bridge.annotation.*;

/**
 * The system clipboard. No state: the clipboard belongs to the system, so both directions ask for it
 * again every time, and a {@code Context} is all either direction needs.
 */
public class Clipboard {
    private final Context context;

    public Clipboard(Context context) {
        this.context = context;
    }

    /** Reads the system clipboard. */
    @ArtDirectHandler
    public String getClipboardText() {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || manager.getPrimaryClip() == null)
            return null;
        ClipData.Item item = manager.getPrimaryClip().getItemAt(0);
        return item == null || item.getText() == null ? null : item.getText().toString();
    }

    /** Writes the system clipboard. */
    @ArtDirectHandler
    public void setClipboardText(String text) {
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null)
            manager.setPrimaryClip(ClipData.newPlainText("mindustry", text));
    }
}
