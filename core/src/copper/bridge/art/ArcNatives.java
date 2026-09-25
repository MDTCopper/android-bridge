package copper.bridge.art;

import copper.bridge.*;

import copper.bridge.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Puts the caller's native libraries on the classpath under the names arc's loader asks for: arc finds
 * each of its libraries itself, as a classpath resource rather than as a path. The naming rule is
 * reproduced from the injected system properties because nothing here touches arc, which is what makes
 * this file shared by every branch.
 */
public final class ArcNatives {
    private ArcNatives() {
    }

    /** Cache subfolder the caller's libraries are staged in. */
    private static final String STAGING_FOLDER = "native/arc";

    /** The logical name of arc's own library: the one {@code ArcNativesLoader} asks for. */
    private static final String ARC = "arc";

    /**
     * The two fixed pieces of the spelling every library handed over through {@code --arc-lib} uses, so
     * the logical name is the file name with these taken off. Nothing validates the name: one ending in
     * {@code arm} or {@code 64} is indistinguishable from one already carrying
     * {@link #mappedLibraryName}'s infix, so the spelling is a contract the caller keeps.
     */
    private static final String PREFIX = "lib";
    private static final String SUFFIX = ".so";

    /**
     * Puts the caller's native libraries on the classpath under the names their loaders ask for. Both layouts
     * are accepted - the libraries directly in the folder, or one subfolder per Android ABI, the ABI this process
     * runs searched first - and nothing is filtered by name beyond the {@code lib<name>.so} shape, because the
     * bridge does not own the list of libraries arc may need.
     *
     * @return the folder that has to be on the classpath, or {@code null} when the caller provided no library
     * at all
     */
    public static File stage() {
        File folder = new File(Bridge.options.cacheFolder, STAGING_FOLDER);
        String arch = Bridge.options.arch == null || Bridge.options.arch.isEmpty()
                ? "aarch64" : Bridge.options.arch;

        List<File> libraries = libraries();
        if (libraries.isEmpty()) {
            Log.error("no native libraries under " + Bridge.options.arcLibPath
                    + "; arc's natives cannot be loaded");
            return null;
        }

        // Staged under the mapped spelling, not under the name it arrived with: renaming only
        // libarc.so left libarc-filedialogs.so unfindable, because arc asks for
        // libarc-filedialogsarm64.so. See PREFIX.
        Map<String, File> wanted = new LinkedHashMap<>();
        String arcName = null;
        for (File library : libraries) {
            String logical = library.getName();
            if (logical.endsWith(SUFFIX))
                logical = logical.substring(0, logical.length() - SUFFIX.length());
            if (logical.startsWith(PREFIX))
                logical = logical.substring(PREFIX.length());
            String name = mappedLibraryName(logical, arch);
            wanted.put(name, library);
            if (ARC.equals(logical))
                arcName = name;
        }

        // Reconciled, not rebuilt: an entry already staged under the right name from the same file is
        // left alone, so a launch that changes nothing does not copy a hundred-megabyte library.
        folder.mkdirs();
        removeUnwanted(folder, wanted);
        for (Map.Entry<String, File> entry : wanted.entrySet()) {
            File staged = new File(folder, entry.getKey());
            if (isCurrent(staged, entry.getValue()))
                continue;
            if (!stage(entry.getValue(), staged))
                continue;
            Log.verbose("staged " + entry.getValue().getName() + " as " + entry.getKey());
        }

        if (arcName != null && new File(folder, arcName).exists())
            Bridge.options.arcNativeName = arcName;
        else
            Log.error("no lib" + ARC + ".so under " + Bridge.options.arcLibPath
                    + "; arc's own native cannot be loaded");

        Bridge.options.arcNativeFolder = folder;
        return folder;
    }

    /**
     * Every library the caller handed over, this device's architecture first: inside a folder the ABI
     * subfolder wins over the files directly in it, because it is the one certainly for this process.
     */
    private static List<File> libraries() {
        File path = Bridge.options.arcLibPath;
        List<File> found = new ArrayList<>();
        if (path == null)
            return found;
        if (path.isFile()) {
            found.add(path);
            return found;
        }

        File byAbi = new File(path, Bridge.options.abi);
        if (byAbi.isDirectory())
            addFiles(byAbi, found);
        if (found.isEmpty())
            addFiles(path, found);
        return found;
    }

    private static void addFiles(File directory, List<File> found) {
        File[] children = directory.listFiles();
        if (children == null)
            return;
        // a stable order, so a lock line says the same thing on every launch
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (child.isFile() && child.getName().endsWith(".so"))
                found.add(child);
        }
    }

    /**
     * The file name arc's loader computes for a library, given its logical name: ARM gets an {@code arm}
     * infix (arc counts {@code aarch64} as ARM) and 64 bit a {@code 64} suffix. Getting it wrong is
     * silent: arc would not find the resource and would fail at the first use.
     *
     * @param logicalName the name arc asks for, e.g. {@code arc} or {@code arc-freetype}
     * @param arch        the same value the bridge injects as {@code os.arch}, i.e. {@link Device#arch()}
     */
    private static String mappedLibraryName(String logicalName, String arch) {
        boolean arm = arch.startsWith("arm") || arch.startsWith("aarch64");
        boolean bits64 = arch.contains("64") || arch.startsWith("armv8");
        return PREFIX + logicalName + (arm ? "arm" : "") + (bits64 ? "64" : "") + SUFFIX;
    }

    /**
     * Puts one library under the staging folder: a symbolic link first, since copying costs the whole
     * size again, and a copy where the file system has no links - Android's
     * {@code getExternalFilesDir} is the usual example.
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
            to.setReadOnly();
            return true;
        } catch (IOException e) {
            Log.error("failed to stage " + from + " as " + to);
            Log.error(e);
            return false;
        }
    }

    /** Drops whatever the folder holds that is not wanted any more, and says whether it dropped one. */
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

    /**
     * Whether a staged entry already is the file it should be. A copy has nothing to compare but its size
     * and its time, and its time is when it was copied, so a source replaced afterwards is newer than its
     * copy and gets copied again.
     */
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
