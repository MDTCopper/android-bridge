package copper.bridge.compat.check;

import copper.bridge.compat.api.JarIndex;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Resolves a class the way the runtime does, layer by layer: the JDK, the branch's own classes, the bridge jar,
 * then the game jar. The bridge jar precedes the game jars on that classpath, so a class it carries is the
 * bridge's even when a game jar carries an older copy of the same name.
 *
 * <p>A class in none of the layers is dangling: javac could resolve it from the epoch jar, but no game version
 * puts it on the runtime classpath. The check reports those instead of guessing a range for them.
 */
public final class ClassOrigins {
    public enum Layer {
        JDK, BRANCH, BRIDGE, GAME, DANGLING
    }

    private final Set<String> branch;
    private final List<ClassSource> bridge;
    private final JarIndex game;

    /**
     * @param branchClasses the classes directory of the branch being checked
     * @param bridgePaths what the bridge jar carries besides its branches: a classes directory or a jar each
     * @param game the epoch jar the branch was compiled against
     */
    public ClassOrigins(Path branchClasses, List<Path> bridgePaths, JarIndex game) {
        this.branch = names(branchClasses);
        this.bridge = new ArrayList<>();
        for (Path path : bridgePaths)
            this.bridge.add(source(path));
        this.game = game;
    }

    public Layer of(String owner) {
        if (jdk(owner))
            return Layer.JDK;
        if (branch.contains(owner))
            return Layer.BRANCH;
        for (ClassSource source : bridge) {
            if (source.contains(owner))
                return Layer.BRIDGE;
        }
        return game.classInfo(owner) == null ? Layer.DANGLING : Layer.GAME;
    }

    /** The JDK's modules only: a classpath hit is not the JDK's, so only the {@code jrt} scheme counts. */
    private static boolean jdk(String owner) {
        URL resource = ClassLoader.getSystemResource(owner + ".class");
        return resource != null && "jrt".equals(resource.getProtocol());
    }

    private static ClassSource source(Path path) {
        try {
            return Files.isDirectory(path) ? new Names(names(path)) : new Jar(new JarIndex(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> names(Path directory) {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> directory.relativize(path).toString().replace('\\', '/'))
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private interface ClassSource {
        boolean contains(String owner);
    }

    private static final class Names implements ClassSource {
        private final Set<String> names;

        Names(Set<String> names) {
            this.names = names;
        }

        @Override
        public boolean contains(String owner) {
            return names.contains(owner);
        }
    }

    private static final class Jar implements ClassSource {
        private final JarIndex index;

        Jar(JarIndex index) {
            this.index = index;
        }

        @Override
        public boolean contains(String owner) {
            return index.classInfo(owner) != null;
        }
    }
}
