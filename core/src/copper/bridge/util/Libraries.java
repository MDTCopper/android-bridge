package copper.bridge.util;

import copper.bridge.func.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Library files: finding the ones the caller handed over, putting one where a loader can map it, and
 * setting the mode that mapping needs.
 */
public class Libraries {
    /**
     * Puts a library at {@code dst}, with the bytes {@code srcProv} opens. A file that already holds those
     * bytes is left where it is, and only its mode is set again.
     *
     * <p>Comparing by content is what lets a caller use a fixed target name, without an ABI or a build in
     * the path, and still not load an old library with no error. A write goes to a temporary file first and
     * is moved into place afterwards, so a crash cannot leave a half written library under the real name.
     * The file is left executable for the owner and read-only: Android will not map a library without the
     * execute bit, and a library anything may write can be mapped half written.</p>
     *
     * @param srcProv opens the library's bytes; it is called once for the comparison and once more for the
     * write, so every call has to return a fresh stream
     */
    public static void extract(ThrowableProv<InputStream> srcProv, File dst) {
        dst.getParentFile().mkdirs();
        File temporary = new File(dst.getParentFile(), dst.getName() + ".tmp");
        try {
            boolean extract;
            if (dst.exists()) {
                try (InputStream srcStream = srcProv.get();
                     InputStream dstStream = new FileInputStream(dst)) {
                    extract = !Streams.contentEquals(srcStream, dstStream);
                }
            } else {
                extract = true;
            }

            if (extract) {
                dst.delete();
                temporary.delete();
                try (InputStream in = srcProv.get();
                     OutputStream out = new FileOutputStream(temporary)) {
                    Streams.pipeStream(in, out, true);
                }
                if (!temporary.setExecutable(true, true))
                    Log.warn("cannot mark " + temporary + " executable");
                Files.move(temporary.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (!dst.setReadOnly())
                    Log.warn("cannot mark " + dst + " readonly");
            } else {
                if (dst.exists())
                    ensureLoadable(dst);
            }
        } catch (Throwable e) {
            throw new RuntimeException("failed to extract " + dst.getName(), e);
        } finally {
            if (temporary.exists())
                temporary.delete();
        }
    }

    /**
     * The first of {@code alternativeNames} that is a file under {@code searchPath}: for each name the ABI
     * subfolder is tried before the folder itself, and one name before the next.
     *
     * @return that file, or {@code null} when none of the names is there
     */
    public static File find(File searchPath, String abi, String ...alternativeNames) {
        for(String name : alternativeNames){
            if(abi != null && !abi.isEmpty()){
                File byAbi = new File(new File(searchPath, abi), name);
                if(byAbi.isFile())
                    return byAbi;
            }
            File flat = new File(searchPath, name);
            if(flat.isFile())
                return flat;
        }
        return null;
    }

    /**
     * The {@code .so} files under {@code searchPath}. Inside a folder the ABI subfolder wins over the files
     * directly in it, because it is the one certainly for this process; the files beside it are taken only
     * when the subfolder holds none. A file handed over on its own is taken when its name ends in
     * {@code .so}.
     *
     * <p>The order is by file name, so the same folder gives the same order on every launch.</p>
     *
     * @return the libraries found, or an empty list when {@code searchPath} is {@code null}
     */
    public static List<File> list(File searchPath, String abi) {
        ArrayList<File> libs = new ArrayList<>();
        if (searchPath == null)
            return libs;
        if (searchPath.isFile()) {
            if (searchPath.getName().endsWith(".so"))
                libs.add(searchPath);
            return libs;
        }

        Cons<File> search = path -> {
            File[] children = path.listFiles();
            if (children == null)
                return;
            // a stable order, so a lock line says the same thing on every launch
            Arrays.sort(children, Comparator.comparing(File::getName));
            for (File child : children) {
                if (child.isFile() && child.getName().endsWith(".so"))
                    libs.add(child);
            }
        };

        File byAbi = new File(searchPath, abi);
        if (byAbi.isDirectory())
            search.get(byAbi);
        if (libs.isEmpty())
            search.get(searchPath);
        return libs;
    }

    /**
     * Sets the mode a loader needs on a library: executable for the owner and read-only. Both steps can
     * fail, the usual reason being a folder the caller cannot write, so this only warns.
     */
    public static void ensureLoadable(File lib) {
        boolean success = true;
        success &= lib.setExecutable(true, true);
        success &= lib.setReadOnly();
        if (!success)
            Log.warn("failed to mark library executable and readonly: " + lib.getAbsolutePath());
    }
}
