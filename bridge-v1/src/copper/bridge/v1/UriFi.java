package copper.bridge.v1;

import arc.Files.FileType;
import arc.files.*;
import arc.util.ArcRuntimeException;
import copper.bridge.gen.*;

import java.io.*;

/**
 * A {@link Fi} for a document the file picker handed back as a URI: a {@code content://} URI that only ART may
 * resolve, so read and write go through the bridge and the game keeps using its normal {@code Fi} export path.
 * The write path is why it exists - a save request has no file when it is answered, so the game produces the save
 * into this handle and the bytes reach ART when the stream closes, as one array or spilling to a file through
 * {@code copyToUri} when too large for the heap. Reads pull the whole document through the heap, which is fine
 * because a document read back is one just written.
 */
public class UriFi extends Fi{
    private final String uri;

    public UriFi(String uri, String displayName){
        this.uri = uri;
        // there is no real path behind a document, so the name is only what dialogs display
        this.file = new File(displayName);
        this.type = FileType.absolute;
    }

    @Override
    public InputStream read(){
        byte[] data = JvmCall.readUri(uri);
        return new ByteArrayInputStream(data == null ? new byte[0] : data);
    }

    @Override
    public OutputStream write(boolean append){
        // a document is one write: appending would mean reading it back and rewriting it, and doing
        // that silently is worse than refusing it where the caller can see it
        if(append)
            throw new ArcRuntimeException("Cannot append to a picked document: " + uri);
        return new DocumentOutputStream(uri);
    }

    @Override
    public boolean exists(){
        return true;
    }

    @Override
    public long length(){
        byte[] data = JvmCall.readUri(uri);
        return data == null ? 0 : data.length;
    }

    @Override
    public String name(){
        return file.getName();
    }

    @Override
    public String extension(){
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    /**
     * Collects one document and hands it to ART when it is closed. A picked document cannot be written
     * incrementally, so the whole thing is staged until it is complete: memory until it grows past
     * {@link #MEMORY_LIMIT}, then a file in the bridge's tmp folder handed over through {@code copyToUri},
     * where ART streams it into the document instead of this VM holding it.
     */
    private static class DocumentOutputStream extends OutputStream{
        /**
         * How much of a document may sit in the JVM heap before it spills to a file. Saves, maps and
         * screenshots are single digit megabytes, so they never spill; a document that does is one the heap
         * should not be holding anyway.
         */
        private static final int MEMORY_LIMIT = 8 * 1024 * 1024;

        private final String uri;
        /** Where a spilled document is staged: the JVM runs with java.io.tmpdir inside the cache. */
        private final File folder;
        private ByteArrayOutputStream memory = new ByteArrayOutputStream();
        /** Where the next bytes go: the memory buffer, or the file once the document spilled. */
        private OutputStream target = memory;
        private File spilled;
        private boolean closed;

        DocumentOutputStream(String uri){
            this.uri = uri;
            this.folder = new File(System.getProperty("java.io.tmpdir", "."));
        }

        @Override
        public void write(int value) throws IOException{
            ensureRoom(1);
            target.write(value);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException{
            ensureRoom(length);
            target.write(data, offset, length);
        }

        @Override
        public void close() throws IOException{
            if(closed)
                return;
            closed = true;

            try{
                if(spilled == null){
                    if(!JvmCall.writeUri(uri, memory.toByteArray()))
                        throw new IOException("failed to write the document: " + uri);
                }else{
                    target.close();
                    if(!JvmCall.copyToUri(uri, spilled.getAbsolutePath()))
                        throw new IOException("failed to copy the file into the document: " + uri);
                }
            }finally{
                // the document has the bytes now, or never will; the staged copy is dead either way
                if(spilled != null)
                    spilled.delete();
            }
        }

        /** Moves the document to a file once keeping it in memory would be the larger mistake. */
        private void ensureRoom(int extra) throws IOException{
            if(spilled != null || memory.size() + extra <= MEMORY_LIMIT)
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
