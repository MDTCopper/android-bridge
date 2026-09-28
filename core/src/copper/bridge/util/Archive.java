package copper.bridge.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * One jar, open for reading: what its central directory says about an entry, and a stream over that entry's
 * bytes. The CRC32 and the size of an entry come from the central directory, so no payload has to be read for
 * them. A handle rather than static calls, because the questions come one after another and the same file would
 * otherwise be opened three times. Writing an entry out as a library is {@link Libraries#extract}'s job.
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

    public static final class Entry {
        private final ZipFile zip;
        private final ZipEntry entry;

        private Entry(ZipFile zip, ZipEntry entry) {
            this.zip = zip;
            this.entry = entry;
        }

        public long crc() {
            return entry.getCrc();
        }

        public long size() {
            return entry.getSize();
        }

        /**
         * A stream over the entry's bytes, read from the jar this entry came from: the caller has to read it
         * before the archive is closed.
         */
        public InputStream read() {
            try {
                return zip.getInputStream(entry);
            } catch (IOException e) {
                throw new RuntimeException("failed to read zip entry " + entry.getName() + " from: " + zip.getName(), e);
            }
        }
    }

    /** Throws when the jar has no such entry: a broken jar, not a caller mistake. */
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

    private ZipEntry found(String name) {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null)
            throw new RuntimeException("no " + name + " in " + jar.getName());
        return entry;
    }
}
