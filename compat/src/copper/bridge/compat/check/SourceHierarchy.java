package copper.bridge.compat.check;

import copper.bridge.compat.api.ClassInfo;
import copper.bridge.compat.api.JavaApi;
import copper.bridge.compat.sources.Git;
import copper.bridge.compat.sources.SourceTree;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Resolves the game/arc supertypes of a bridge method through the source trees of one git revision. */
final class SourceHierarchy {
    private final SourceTree game;
    private final SourceTree arc;
    private final Map<String, ClassInfo> branch = new HashMap<>();

    SourceHierarchy(Path gameClone, String gameCommit, Path arcClone, String arcCommit, Path bridgeClasses) {
        this.game = new SourceTree(new Git(gameClone.toString()), gameCommit, false);
        this.arc = new SourceTree(new Git(arcClone.toString()), arcCommit, true);
        try (Stream<Path> stream = Files.walk(bridgeClasses)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                String name = bridgeClasses.relativize(path).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                try {
                    branch.put(name, ClassInfo.parse(Files.readAllBytes(path)));
                } catch (Throwable ignored) {
                    // the branch classes were already read by the other checks
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean gameMatch(String owner, String member) {
        ClassInfo info = branch.get(owner);
        if (info == null)
            return false;

        Deque<String> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        if (info.superName != null && seen.add(info.superName))
            queue.add(info.superName);
        for (String itf : info.interfaces)
            if (seen.add(itf))
                queue.add(itf);

        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            SourceTree tree = game.resolve(name) != null ? game : arc;
            String path = tree.resolve(name);
            String outer = name.contains("$") ? name.substring(0, name.indexOf('$')) : name;
            String outerSimple = outer.substring(outer.lastIndexOf('/') + 1);
            String nested = name.contains("$") ? name.substring(name.lastIndexOf('$') + 1) : null;
            if (path == null) {
                // a simple name from an extends/implements clause, not an internal name
                path = game.resolveSimple(name);
                tree = path != null ? game : arc;
                if (path == null)
                    path = arc.resolveSimple(name);
                outerSimple = name;
                nested = null;
            }
            if (path == null)
                continue;
            JavaApi.Type type = JavaApi.type(tree.source(path), path, outerSimple, nested);
            if (type == null)
                continue;
            if (!JavaApi.members(type.tree, member).isEmpty())
                return true;
            for (String parent : JavaApi.supertypes(type.tree))
                if (seen.add(parent))
                    queue.add(parent);
        }
        return false;
    }
}
