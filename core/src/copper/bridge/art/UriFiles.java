package copper.bridge.art;

import android.content.*;

import copper.bridge.annotation.*;
import copper.bridge.util.*;
import java.io.*;

/**
 * Reads and writes documents the game was handed as a {@code content://} URI. A picked document is not a
 * file, and the JVM side has no content resolver, so the bytes either travel as an array or are streamed
 * here. {@link #readUri} keeps its checked exception on purpose: a direct call that throws is reported in the
 * native log and answered with the neutral value, so the caller sees a null array rather than a broken game
 * loop.
 */
public class UriFiles {
    private final Context context;

    public UriFiles(Context context) {
        this.context = context;
    }

    /**
     * Reads a whole document into memory, for the JVM side, which cannot open one itself. A document too
     * large for that goes through {@link #copyToUri} instead.
     */
    @ArtDirectHandler
    public byte[] readUri(String uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
            if (in == null)
                throw new IOException("the document cannot be read");
            return Streams.readAllBytes(in);
        }
    }

    /** Writes a whole document from memory. */
    @ArtDirectHandler
    public boolean writeUri(String uri, byte[] data) {
        try (OutputStream out = context.getContentResolver()
                .openOutputStream(android.net.Uri.parse(uri), "rwt")) {
            if (out == null)
                return false;
            out.write(data);
            return true;
        } catch (Throwable e) {
            Log.warn("cannot write " + uri + ": " + e);
            return false;
        }
    }

    /** Streams a file into a document, for one too large to pass through memory. */
    @ArtDirectHandler
    public boolean copyToUri(String uri, String path) {
        try (InputStream in = new FileInputStream(path);
             OutputStream out = context.getContentResolver()
                     .openOutputStream(android.net.Uri.parse(uri), "rwt")) {
            if (out == null)
                return false;
            Streams.pipeStream(in, out, false);
            return true;
        } catch (Throwable e) {
            Log.warn("cannot copy " + path + " into " + uri + ": " + e);
            return false;
        }
    }
}
