package copper.bridge.art;

import android.app.*;
import android.content.*;
import android.net.*;

import copper.bridge.*;
import copper.bridge.annotation.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * The system file picker, and the staging that turns its answers into something the game can read: the request id is
 * packed into the request code, so two pickers cannot overwrite each other's answer.
 */
public class FileChooser {
    /** Marks a request code as one this activity started: the low bits hold the id, one bit says open or save. */
    private static final int PICKER_RESULT = 1 << 29;
    private static final int PICKER_SAVE = 1 << 30;
    /** The bits the id may use: everything below the marker, so an id can never look like one. */
    private static final int PICKER_MASK = PICKER_RESULT - 1;

    private final Activity activity;

    public FileChooser(Activity activity) {
        this.activity = activity;
    }

    @ArtPostHandler(callbacks = {"result(String[])", "error(String)", "canceled()"})
    public void showFileChooser(long request, boolean open, boolean multiple, String title,
                                String fileName, String[] extensions) {
        try {
            String extension = extensions != null && extensions.length > 0 ? extensions[0] : "";
            Intent intent = new Intent(open ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // The picker filters by mime type and a game file has no registered type; only a zip is worth naming.
            intent.setType(!open && "zip".equals(extension) ? "application/zip" : "*/*");
            if (fileName != null && !fileName.isEmpty())
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
            if (multiple)
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

            activity.startActivityForResult(intent, pickerCode(request, open));
        } catch (Throwable e) {
            Log.warn("cannot open the file picker: " + e);
            ArtCall.showFileChooserError(request, "cannot open the file picker: " + e);
        }
    }

    public void activityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode & PICKER_RESULT) == 0)
            return;

        long request = requestCode & PICKER_MASK;
        boolean open = (requestCode & PICKER_SAVE) == 0;
        if (request == 0)
            return;

        if (resultCode != Activity.RESULT_OK || data == null) {
            ArtCall.showFileChooserCanceled(request);
            return;
        }

        try {
            List<Uri> picked = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    picked.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                picked.add(data.getData());
            }

            List<String> paths = new ArrayList<>();
            for (Uri uri : picked)
                paths.add(stage(uri, open));
            ArtCall.showFileChooserResult(request, paths.toArray(new String[0]));
        } catch (Throwable e) {
            Log.warn("cannot use the picked file: " + e);
            ArtCall.showFileChooserError(request, "cannot use the chosen file: " + e);
        }
    }

    public void clearStaged() {
        File[] leftovers = pickedFolder().listFiles();
        if (leftovers == null)
            return;
        for (File file : leftovers)
            file.delete();
    }

    private static int pickerCode(long request, boolean open) {
        return PICKER_RESULT | (open ? 0 : PICKER_SAVE) | (int) (request & PICKER_MASK);
    }

    /** Turns a document the picker returned into something the game can use. An open request becomes a real file: a
     *  {@code content://} URI is copied into the cache folder. A save request's URI goes over to be written. */
    private String stage(Uri uri, boolean open) throws IOException {
        if (!open)
            return uri.toString();

        File target = new File(pickedFolder(), uniqueName(uri));
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null)
                throw new IOException("the picked document cannot be read");
            Streams.pipeStream(in, out, true);
        }
        return target.getAbsolutePath();
    }

    private File pickedFolder() {
        File folder = new File(Bridge.options.cacheFolder, "picked");
        folder.mkdirs();
        return folder;
    }

    private static String uniqueName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty())
            name = "file";
        return System.currentTimeMillis() + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
