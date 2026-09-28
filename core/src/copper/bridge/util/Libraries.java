package copper.bridge.util;

import copper.bridge.func.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Libraries {
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

    public static void ensureLoadable(File lib) {
        boolean success = true;
        success &= lib.setExecutable(true, true);
        success &= lib.setReadOnly();
        if (!success)
            Log.warn("failed to mark arc library executable and readonly: " + lib.getAbsolutePath());
    }
}
