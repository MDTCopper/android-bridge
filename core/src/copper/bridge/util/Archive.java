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
public final class Archive implements Closeable {
    private final ZipFile jar;

    private Archive(ZipFile jar) {
        this.jar = jar;
    }

    /** Opens a jar for reading; the caller closes it. */
    public static Archive open(File jar) {
        try {
            return new Archive(new ZipFile(jar));
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + jar, e);
        }
    }

    /** What the central directory knows about one entry. */
    public static final class Entry {
        private final ZipFile zip;
        private final ZipEntry entry;

        private Entry(ZipFile zip, ZipEntry entry) {
            this.zip = zip;
            this.entry = entry;
        }

        /** The CRC32 of the entry, for a caller's log line. It comes from the central directory. */
        public long crc() {
            return entry.getCrc();
        }

        /** The uncompressed size, for a caller's log line. */
        public long size() {
            return entry.getSize();
        }

        public InputStream read() {
            try {
                return zip.getInputStream(entry);
            } catch (IOException e) {
                throw new RuntimeException("failed to read zip entry " + entry.getName() + " from: " + zip.getName(), e);
            }
        }
    }

    /** One entry's facts; a jar without it is a broken jar, not a caller mistake. */
    public Entry entry(String name) {
        ZipEntry found = found(name);
        return new Entry(jar, found);
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
