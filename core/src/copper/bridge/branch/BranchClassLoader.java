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
 */
public final class BranchClassLoader extends ClassLoader {
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
                    byte[] bytes = new byte[length];
                    data.readFully(bytes);
                    if (!name.startsWith(prefix) || !name.endsWith(".class"))
                        continue;
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
                } else if (isGame(name)) {
                    loaded = loadGame(name);
                }

                if (loaded == null)
                    loaded = super.loadClass(name, false);
            }

            if (resolve)
                resolveClass(loaded);
            return loaded;
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
            if (jar.getJarEntry(name) == null)
                continue;
            try {
                return new URI("jar:" + new File(jar.getName()).toURI().toURL() + "!/" + name).toURL();
            } catch (URISyntaxException | MalformedURLException ignored) {
            }
        }
        return null;
    }

    private static boolean isGame(String name) {
        return name.startsWith("arc.") || name.startsWith("io.anuke.arc.")
                || name.startsWith("mindustry.") || name.startsWith("io.anuke.mindustry.");
    }
}
