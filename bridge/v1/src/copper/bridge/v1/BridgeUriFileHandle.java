package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.gen.JvmCall;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.*;

/**
 * A handle for a document the file picker handed back as a URI: a {@code content://} URI that only ART may
 * resolve, so read and write go through the bridge. The write path is why it exists - a save request has no file
 * when it is answered, so the game produces the save into this handle and the bytes reach ART when the stream
 * closes.
 */
public class BridgeUriFileHandle extends FileHandle {
    private final String uri;

    public BridgeUriFileHandle(String uri, String displayName) {
        this.uri = uri;
        // there is no real path behind a document, so the name is only what dialogs display
        this.file = new File(displayName);
        this.type = FileType.Absolute;
    }

    @Override
    @BridgeOverride
    public InputStream read() {
        byte[] data = JvmCall.readUri(uri);
        return new ByteArrayInputStream(data == null ? new byte[0] : data);
    }

    @Override
    @BridgeOverride
    public OutputStream write(boolean append) {
        if (append)
            throw new GdxRuntimeException("cannot append to a picked document: " + uri);
        return new DocumentOutputStream(uri);
    }

    @Override
    @BridgeOverride
    public boolean exists() {
        return true;
    }

    @Override
    @BridgeOverride
    public long length() {
        byte[] data = JvmCall.readUri(uri);
        return data == null ? 0 : data.length;
    }

    /**
     * Collects one document and hands it to ART when it is closed. A picked document cannot be written
     * incrementally, so the whole thing is staged until it is complete.
     */
    private static class DocumentOutputStream extends OutputStream {
        /** How much of a document may sit in the JVM heap before it spills to a file. */
        private static final int MEMORY_LIMIT = 8 * 1024 * 1024;

        private final String uri;
        private final File folder;
        private ByteArrayOutputStream memory = new ByteArrayOutputStream();
        private OutputStream target = memory;
        private File spilled;
        private boolean closed;

        DocumentOutputStream(String uri) {
            this.uri = uri;
            this.folder = new File(System.getProperty("java.io.tmpdir", "."));
        }

        @Override
        public void write(int value) throws IOException {
            ensureRoom(1);
            target.write(value);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            ensureRoom(length);
            target.write(data, offset, length);
        }

        @Override
        public void close() throws IOException {
            if (closed)
                return;
            closed = true;

            try {
                if (spilled == null) {
                    if (!JvmCall.writeUri(uri, memory.toByteArray()))
                        throw new IOException("failed to write the document: " + uri);
                } else {
                    target.close();
                    if (!JvmCall.copyToUri(uri, spilled.getAbsolutePath()))
                        throw new IOException("failed to copy the file into the document: " + uri);
                }
            } finally {
                if (spilled != null)
                    spilled.delete();
            }
        }

        private void ensureRoom(int extra) throws IOException {
            if (spilled != null || memory.size() + extra <= MEMORY_LIMIT)
                return;

            folder.mkdirs();
            File file = File.createTempFile("copper-document-", ".tmp", folder);
            OutputStream stream = new FileOutputStream(file);
            memory.writeTo(stream);

            spilled = file;
            memory = null;
            target = stream;
        }
    }
}
