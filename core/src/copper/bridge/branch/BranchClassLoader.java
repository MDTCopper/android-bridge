package copper.bridge.branch;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Loads one branch's classes from the bridge jar's {@code branches.deflate} stream. Game and core classes come from
 * the startup loader, which has the game jars on its classpath; only the branch's own classes are defined here.
 *
 * <p>The stream is shared by every branch so the same class in v1..v9 sits next to itself and deflate finds the
 * common bytes; only the selected {@code vN/} prefix is kept in memory. Format: int count, then for each entry a
 * UTF {@code vN/copper/bridge/vN/...class} name, an int byte length, and the class bytes.
 */
public final class BranchClassLoader extends ClassLoader {
    /** Branch classes not defined yet; an entry is dropped as soon as its class is defined. */
    private final Map<String, byte[]> branchClasses = new HashMap<>();

    public BranchClassLoader(String branch, ClassLoader parent) throws IOException {
        super(parent);

        String resource = "branches.deflate";
        try (InputStream raw = parent.getResourceAsStream(resource)) {
            if (raw == null)
                throw new IOException("no branch stream " + resource + " in the startup classpath");
            String prefix = branch + "/";
            try (DataInputStream data = new DataInputStream(new InflaterInputStream(raw))) {
                int count = data.readInt();
                for (int i = 0; i < count; i++) {
                    String name = data.readUTF();
                    int length = data.readInt();
                    if (!name.startsWith(prefix) || !name.endsWith(".class")) {
                        skipFully(data, length);
                        continue;
                    }
                    byte[] bytes = new byte[length];
                    data.readFully(bytes);
                    String className = name.substring(prefix.length(), name.length() - ".class".length());
                    branchClasses.put(className, bytes);
                }
            }
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                String internal = name.replace('.', '/');
                byte[] branch = branchClasses.get(internal);
                if (branch != null) {
                    loaded = defineClass(name, branch, 0, branch.length);
                    // the VM owns the class now; keeping the bytes would only double its memory
                    branchClasses.remove(internal);
                } else {
                    // game, core and JDK classes all belong to the startup loader
                    loaded = super.loadClass(name, false);
                }
            }

            if (resolve)
                resolveClass(loaded);
            return loaded;
        }
    }

    private static void skipFully(DataInputStream in, int count) throws IOException {
        int remaining = count;
        while (remaining > 0) {
            int skipped = in.skipBytes(remaining);
            if (skipped == 0) {
                if (in.read() < 0)
                    throw new EOFException("truncated branch stream");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
