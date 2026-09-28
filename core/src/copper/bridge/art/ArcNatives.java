package copper.bridge.art;

import copper.bridge.*;

import copper.bridge.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Stages the caller's native libraries under the names arc's loader asks for: arc looks its libraries up by name.
 */
public final class ArcNatives {
    private ArcNatives() {
    }

    private static final String STAGING_FOLDER = "native/arc";

    /** The file name arc's own library has to arrive under: the one {@code ArcNativesLoader} asks for. */
    private static final String ARC = "libarc.so";

    /**
     * Stages the caller's libraries under the names they arrived with, and records the folder in
     * {@link BridgeOptions#arcNativeFolder}. The caller has to spell them the way arc asks, ABI infix included
     * ({@code libarc-filedialogsarm64.so}): nothing is renamed, and arc reports a missing library as nothing.
     */
    public static void stage() {
        File folder = new File(Bridge.options.cacheFolder, STAGING_FOLDER);

        List<File> libraries = Libraries.list(Bridge.options.arcLibPath, Bridge.options.abi);
        if (libraries.isEmpty()) {
            Log.error("no native libraries under " + Bridge.options.arcLibPath
                    + "; arc's natives cannot be loaded");
            return;
        }

        // keyed by the file's own name, which is the name arc asks for later; nothing here renames anything
        Map<String, File> wanted = new LinkedHashMap<>();
        boolean foundArc = false;
        for (File library : libraries) {
            Libraries.ensureLoadable(library);
            String name = library.getName();
            wanted.put(name, library);
            if (ARC.equals(name))
                foundArc = true;
        }

        // reconciled, not rebuilt: an entry already staged from the same file is left alone
        folder.mkdirs();
        removeUnwanted(folder, wanted);
        for (Map.Entry<String, File> entry : wanted.entrySet()) {
            File staged = new File(folder, entry.getKey());
            if (isCurrent(staged, entry.getValue()))
                continue;
            if (!stage(entry.getValue(), staged))
                continue;
            Log.verbose("staged " + entry.getKey());
        }

        if (!foundArc)
            Log.error("no " + ARC + " under " + Bridge.options.arcLibPath
                    + "; arc's own native cannot be loaded");

        Bridge.options.foundArcNative = foundArc;
        Bridge.options.arcNativeFolder = folder;
    }

    /**
     * Puts one library under the staging folder: a symbolic link first, a copy where the file system has no links.
     * A copy needs the mode set again, or Android will not map it.
     */
    private static boolean stage(File from, File to) {
        try {
            if (to.exists())
                to.delete();
            Files.createSymbolicLink(to.toPath(), Paths.get(from.getAbsolutePath()));
            return true;
        } catch (Throwable e) {
            // unsupported, not permitted, or already there: any of the three means copying
        }

        try {
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
                Streams.pipeStream(in, out, true);
            }
            Libraries.ensureLoadable(to);
            return true;
        } catch (IOException e) {
            Log.error("failed to stage " + from + " as " + to);
            Log.error(e);
            return false;
        }
    }

    private static boolean removeUnwanted(File folder, Map<String, File> wanted) {
        File[] children = folder.listFiles();
        if (children == null)
            return false;
        boolean removed = false;
        for (File child : children) {
            if (wanted.containsKey(child.getName()))
                continue;
            delete(child);
            removed = true;
        }
        return removed;
    }

    /** Whether a staged entry already is the file it should be: a copy has only size and time to compare. */
    private static boolean isCurrent(File staged, File source) {
        if (!staged.exists())
            return false;
        try {
            if (Files.isSymbolicLink(staged.toPath()))
                return Files.readSymbolicLink(staged.toPath()).equals(Paths.get(source.getAbsolutePath()));
        } catch (IOException e) {
            return false;
        }
        return staged.length() == source.length() && staged.lastModified() >= source.lastModified();
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                delete(child);
        file.delete();
    }
}
