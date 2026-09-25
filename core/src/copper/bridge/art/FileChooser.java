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
 * The system file picker, and the staging that turns its answers into something the game can read.
 *
 * <p>The answer arrives at the activity and is handed straight here: the request id comes back through
 * {@code onActivityResult} carrying nothing else, so it is packed into the request code - which is what lets
 * the picker keep no record of what is in flight, so two pickers cannot overwrite each other's answer and
 * there is nothing to reset when one is dismissed.</p>
 */
public class FileChooser {
    /**
     * Marks a request code as one this activity started itself. The low bits hold the id, one bit says
     * whether the picker was open or save, and this bit keeps an unrelated result out.
     */
    private static final int PICKER_RESULT = 1 << 29;
    private static final int PICKER_SAVE = 1 << 30;
    /** The bits the id may use: everything below the marker, so an id can never look like one. */
    private static final int PICKER_MASK = PICKER_RESULT - 1;

    private final Activity activity;

    public FileChooser(Activity activity) {
        this.activity = activity;
    }

    /**
     * Opens the system file picker. Runs on the main thread; the title is ignored because the system picker
     * has no place to put one.
     */
    @ArtPostHandler(callbacks = {"result(String[])", "error(String)", "canceled()"})
    public void showFileChooser(long request, boolean open, boolean multiple, String title,
                                String fileName, String[] extensions) {
        try {
            String extension = extensions != null && extensions.length > 0 ? extensions[0] : "";
            Intent intent = new Intent(open ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // The picker filters by mime type, and a game file has no registered type; a zip is the
            // one exception worth naming, because the archive type is what the user is looking for.
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

    /** Takes the answer the system delivered to the activity. */
    public void activityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode & PICKER_RESULT) == 0)
            return;

        long request = requestCode & PICKER_MASK;
        boolean open = (requestCode & PICKER_SAVE) == 0;
        if (request == 0)
            return;

        if (resultCode != Activity.RESULT_OK || data == null) {
            // A dismissed picker is a cancellation, not a failure.
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

    /** Drops whatever an earlier launch left in the staged folder. */
    public void clearStaged() {
        File[] leftovers = pickedFolder().listFiles();
        if (leftovers == null)
            return;
        for (File file : leftovers)
            file.delete();
    }

    /** The request code that carries one picker's identity through the system. */
    private static int pickerCode(long request, boolean open) {
        return PICKER_RESULT | (open ? 0 : PICKER_SAVE) | (int) (request & PICKER_MASK);
    }

    /**
     * Turns a document the picker returned into something the game can use. An open request has to become a
     * real file: a picked document is a {@code content://} URI, which the JVM side cannot read, so it is
     * copied into the cache folder and the copy is what the game gets. A save request stays a document -
     * nothing is written yet - so its URI is handed over and the game writes it through
     * {@code JvmCall.writeUri} or {@code copyToUri} when it has produced the file.
     */
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

    /** The cache folder picked files are staged in. */
    private File pickedFolder() {
        File folder = new File(Bridge.options.cacheFolder, "picked");
        folder.mkdirs();
        return folder;
    }

    /** A name for a picked document that cannot collide with an earlier pick. */
    private static String uniqueName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty())
            name = "file";
        return System.currentTimeMillis() + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
