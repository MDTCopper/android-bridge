package copper.bridge.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * One jar, open for reading: what its central directory says about an entry, and taking one out.
 *
 * <p>The size and the build of an entry come from the zip central directory, so no payload has to be read
 * for them. Only taking an entry out reads bytes, and it does that just to see whether the cached copy is
 * already the same. This class is a handle, not a set of static calls, because the questions come one after
 * another and the same file would otherwise be opened three times. An entry is written under a temporary
 * name and then moved into place, because a file that is half written is a failure the cache cannot see.</p>
 */
public final class Archives implements Closeable {
    private final ZipFile jar;

    private Archives(ZipFile jar) {
        this.jar = jar;
    }

    /** Opens a jar for reading; the caller closes it. */
    public static Archives open(File jar) {
        try {
            return new Archives(new ZipFile(jar));
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + jar, e);
        }
    }

    /** What the central directory knows about one entry. */
    public static final class Entry {
        private final long crc;
        private final long size;

        private Entry(long crc, long size) {
            this.crc = crc;
            this.size = size;
        }

        /** The CRC32 of the entry, for a caller's log line. It comes from the central directory. */
        public long crc() {
            return crc;
        }

        /** The uncompressed size, for a caller's log line. */
        public long size() {
            return size;
        }
    }

    /** One entry's facts; a jar without it is a broken jar, not a caller mistake. */
    public Entry entry(String name) {
        ZipEntry found = found(name);
        return new Entry(found.getCrc(), found.getSize());
    }

    /**
     * Writes one entry out as a file that can be mapped as a library. If the file already holds the jar
     * entry's bytes, nothing is written. That check is what makes a fixed target name safe: the path holds no
     * ABI and no build, so without it an old library could be loaded with no error.
     *
     * <p>When it writes, the copy first goes to a temporary name and is then moved into place. A crash in the
     * middle leaves the temporary file behind, not a broken library under the real name. The permissions are
     * set before the move, because Android will not map a library that is not executable and a zip entry
     * carries no permissions. The file is left executable for the owner and read-only, because a library that
     * anyone may write can be mapped half written.</p>
     */
    public void extractLibrary(String name, File target) {
        ZipEntry entry = found(name);
        target.getParentFile().mkdirs();
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            boolean extract;
            if (target.exists()) {
                try (InputStream src = jar.getInputStream(entry);
                     InputStream dst = new FileInputStream(target)) {
                    extract = !Streams.contentEquals(src, dst);
                }
            } else {
                extract = true;
            }

            if (extract) {
                target.delete();
                temporary.delete();
                try (InputStream in = jar.getInputStream(entry);
                     OutputStream out = new FileOutputStream(temporary)) {
                    Streams.pipeStream(in, out, true);
                }
                if (!temporary.setExecutable(true, true))
                    Log.warn("cannot mark " + temporary + " executable");
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (!target.setReadOnly())
                    Log.warn("cannot mark " + target + " readonly");
            }
        } catch (Throwable e) {
            throw new RuntimeException("failed to extract " + name, e);
        } finally {
            if (temporary.exists())
                temporary.delete();
        }
    }

    @Override
    public void close() {
        try {
            jar.close();
        } catch (IOException e) {
            Log.warn("cannot close " + jar.getName() + ": " + e);
        }
    }

    /** One entry of the open jar; a jar without it is a broken jar, not a caller mistake. */
    private ZipEntry found(String name) {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null)
            throw new RuntimeException("no " + name + " in " + jar.getName());
        return entry;
    }
}
