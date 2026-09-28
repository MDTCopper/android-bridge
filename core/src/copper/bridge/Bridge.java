package copper.bridge;

import copper.bridge.util.*;
import java.io.*;

/**
 * The shared half of the bridge: the facts both virtual machines work from, and entering the native library. A
 * native method binds to the VM whose {@code JNI_OnLoad} registered it, so the same file is opened once per side.
 * Binding is deliberately not here: each subsystem owns its door.
 */
public final class Bridge {

    /** The one options instance: ART parses into it, the JVM reads it back from the system properties. */
    public static BridgeOptions options;

    private static boolean loaded;

    private Bridge() {
    }

    /**
     * Makes sure this VM has a native library to load, and returns the file the JVM side has to load too. The cache
     * path is fixed, so an old library left there would load with no error; {@link Libraries#extract} rules that out.
     */
    public static File prepare() {
        String name = "libcopperbridge.so";
        String entryName = "native/bridge/" + options.abi + "/" + name;

        File jar = jar();
        if (jar == null)
            throw new RuntimeException("cannot locate the bridge jar; pass --bridge-jar <path>");

        try (Archive archive = Archive.open(jar)) {
            Archive.Entry entry = archive.entry(entryName);

            File cached = new File(options.cacheFolder, "native/bridge/" + name);
            Log.verbose("extracting the bridge native library (crc=0x"
                    + Long.toHexString(entry.crc()) + ", " + entry.size() + " bytes)");
            Libraries.extract(entry::read, cached);
            options.bridgeLibrary = cached;
            return cached;
        }
    }

    /** Enters the native library for this VM; throws when no library was prepared for this side. */
    public static synchronized void load() {
        if (loaded)
            return;

        if (options.bridgeLibrary == null)
            throw new RuntimeException("the options name no bridge library: the ART side did not"
                    + " record where it extracted one");

        System.load(options.bridgeLibrary.getAbsolutePath());
        loaded = true;
    }

    /** The bridge jar, or {@code null} when neither the host nor a class loader can name it. */
    public static File jar() {
        if (options != null && options.bridgeJar != null && options.bridgeJar.isFile())
            return options.bridgeJar;

        try {
            java.net.URL resource = Bridge.class.getResource("Bridge.class");
            if (resource != null && "jar".equals(resource.getProtocol())) {
                String path = resource.getPath();
                int end = path.lastIndexOf("!/");
                if (end > 0) {
                    File file = new File(java.net.URI.create(path.substring(0, end)));
                    if (file.isFile())
                        return file;
                }
            }
        } catch (Throwable e) {
            Log.warn("cannot read the bridge jar location: " + e);
        }

        Log.warn("no bridge jar: pass --bridge-jar <path>, bridge resources will be unreachable");
        return null;
    }
}
