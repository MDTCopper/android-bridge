package copper.bridge.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * One jar, open for reading: what its central directory says about an entry, and taking one out.
 *
 * <p>Every answer comes from the zip central directory, so nothing has to read the payload to know an entry's
 * size or which build it came from. It is a handle, not a set of static calls, because the questions come in a
 * row and the same file would otherwise be opened three times: three descriptors and three central-directory
 * parses for one answer. An entry is written under a temporary name and then moved into place, because a file
 * that exists and is half written is the failure a cache cannot see.</p>
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

        /** The CRC32 of the entry, which is what a file extracted from it is named by. */
        public long crc() {
            return crc;
        }

        /** The uncompressed size, which is what a cache's copy is checked against. */
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
     * Writes one entry out as a file that can be mapped as a library. Two details make it one: the copy lands
     * under a temporary name and is then moved into place, so a crash halfway leaves the temporary behind
     * instead of a broken library under the name everything trusts; and the permissions are set before that,
     * because Android refuses to map a library that is not executable and a zip entry carries no permissions
     * at all. The result is left read-only, since a cached library that anything may write can be mapped half
     * written.
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
