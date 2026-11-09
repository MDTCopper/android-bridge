package copper.bridge.branch;

import copper.bridge.Bridge;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Loads one branch's classes from the bridge jar's {@code branches.deflate} stream and the game classes from the game
 * jars. Core classes are delegated to the startup loader; game and branch classes are defined here first so both
 * sides of a branch see the same loader.
 *
 * <p>The stream is shared by every branch so the same class in v1..v9 sits next to itself and deflate finds the
 * common bytes; only the selected {@code vN/} prefix is kept in memory. Format: int count, then for each entry a
 * UTF {@code vN/copper/bridge/vN/...class} name, an int byte length, and the class bytes.
 */
public final class BranchClassLoader extends ClassLoader {
    /** Branch classes not defined yet; an entry is dropped as soon as its class is defined. */
    private final Map<String, byte[]> branchClasses = new HashMap<>();
    private final List<JarFile> gameJars = new ArrayList<>();

    public BranchClassLoader(String branch, List<File> jars, ClassLoader parent) throws IOException {
        super(parent);

        String resource = "branches.deflate";
        try (InputStream raw = parent.getResourceAsStream(resource)) {
            if (raw == null)
                throw new IOException("no branch stream " + resource + " in " + Bridge.jar());
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

        for (File file : jars)
            gameJars.add(new JarFile(file));
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
                    // not a branch class: the parent owns bridge/core classes and must win over the game jar
                    try {
                        loaded = super.loadClass(name, false);
                    } catch (ClassNotFoundException parentMiss) {
                        // only a class the parent does not know comes from the game jar
                        loaded = loadGame(name);
                        if (loaded == null)
                            throw parentMiss;
                    }
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

    private Class<?> loadGame(String name) {
        String path = name.replace('.', '/') + ".class";
        for (JarFile jar : gameJars) {
            JarEntry entry = jar.getJarEntry(path);
            if (entry == null)
                continue;
            try (InputStream in = jar.getInputStream(entry)) {
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    @Override
    protected URL findResource(String name) {
        for (JarFile jar : gameJars) {
            if (jar.getJarEntry(name) != null)
                return jarUrl(jar, name);
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        List<URL> urls = new ArrayList<>();
        for (JarFile jar : gameJars) {
            if (jar.getJarEntry(name) == null)
                continue;
            URL url = jarUrl(jar, name);
            if (url != null)
                urls.add(url);
        }
        return Collections.enumeration(urls);
    }

    private static URL jarUrl(JarFile jar, String name) {
        try {
            return new URI("jar:" + new File(jar.getName()).toURI().toURL() + "!/" + name).toURL();
        } catch (URISyntaxException | MalformedURLException ignored) {
            return null;
        }
    }

}
