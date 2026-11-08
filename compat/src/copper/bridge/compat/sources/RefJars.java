package copper.bridge.compat.sources;

import copper.bridge.compat.api.JarIndex;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * The staged epoch jars, by ref. The build fetches and stages them - see gradle/epochs.gradle - so this class
 * never downloads: a ref the build did not stage is a declaration error and is reported as one.
 */
public final class RefJars implements Closeable {
    private final Map<String, Path> paths = new HashMap<>();
    private final Map<String, JarIndex> jars = new HashMap<>();

    public void add(String ref, Path jar) {
        paths.put(ref, jar);
    }

    /** The jar staged for this release tag or bleeding-edge build id. */
    public JarIndex jar(String ref) {
        JarIndex index = jars.get(ref);
        if (index != null)
            return index;

        Path path = paths.get(ref);
        if (path == null)
            throw new IllegalStateException("no epoch jar staged for ref " + ref);
        try {
            index = new JarIndex(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        jars.put(ref, index);
        return index;
    }

    @Override
    public void close() {
        for (JarIndex index : jars.values())
            index.close();
    }
}
