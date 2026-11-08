package copper.bridge.compat.sources;

import java.util.HashMap;
import java.util.Map;

/**
 * The Java sources of one commit, addressed by class name.
 *
 * <p>The source layout differs between the two repositories and across their history, so the lookup matches the
 * class path suffix and prefers the module root each repository uses. A class with no match is a real absence,
 * not a lookup miss.
 */
public final class SourceTree {
    private final Git git;
    private final String commit;
    private final boolean arc;
    private final Map<String, String> paths = new HashMap<>();
    private final Map<String, String> sources = new HashMap<>();

    public SourceTree(Git git, String commit, boolean arc) {
        this.git = git;
        this.commit = commit;
        this.arc = arc;
        for (String line : git.run("ls-tree", "-r", "--name-only", commit).split("\n")) {
            line = line.trim();
            if (line.endsWith(".java"))
                paths.put(line, line);
        }
    }

    /** The source path of an internal class name such as {@code arc/struct/Seq}, or null when it has none. */
    public String resolve(String classInternalName) {
        String base = classInternalName.contains("$")
                ? classInternalName.substring(0, classInternalName.indexOf('$'))
                : classInternalName;
        String suffix = "/" + base + ".java";
        String preferred = arc ? "arc-core/src/" : "core/src/";
        String fallback = null;
        for (String path : paths.keySet()) {
            if (!(path.equals(base + ".java") || path.endsWith(suffix)))
                continue;
            if (path.contains(preferred))
                return path;
            if (fallback == null)
                fallback = path;
        }
        return fallback;
    }

    public String source(String path) {
        return sources.computeIfAbsent(path, p -> git.show(commit, p));
    }
}
