package copper.bridge.compat.api;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Reads class declarations out of one desktop jar, one class at a time. */
public final class JarIndex implements Closeable {
    private final JarFile jar;
    private final Map<String, ClassInfo> classes = new HashMap<>();

    public JarIndex(Path path) throws IOException {
        jar = new JarFile(path.toFile());
    }

    /** The class with this internal name, or null when the jar has none. */
    public ClassInfo classInfo(String internalName) {
        if (classes.containsKey(internalName))
            return classes.get(internalName);

        ClassInfo info = null;
        JarEntry entry = jar.getJarEntry(internalName + ".class");
        if (entry != null) {
            try (InputStream input = jar.getInputStream(entry)) {
                info = parse(input.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        classes.put(internalName, info);
        return info;
    }

    private static ClassInfo parse(byte[] bytes) {
        return ClassInfo.parse(bytes);
    }

    @Override
    public void close() {
        try {
            jar.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
