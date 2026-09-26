package copper.bridge;

import copper.bridge.annotation.*;
import copper.bridge.util.*;
import java.io.*;

/**
 * The shared half of the bridge: the facts both virtual machines work from, and entering the native library.
 *
 * <p>A native method binds to the VM whose {@code JNI_OnLoad} registered it, so the same file is opened once
 * per side, and the per-class-loader flag is the right scope on both. Binding is deliberately not here: each
 * subsystem owns its door, because a shared entry point could not know which half an object declared, and
 * guessing wrong drops the frames of a channel whose receiver was not asked.</p>
 */
public final class Bridge {

    /**
     * The one options instance of this VM: the ART side parses its command line into it, the JVM
     * side reads it back from the passed system properties.
     */
    public static BridgeOptions options;

    private static boolean loaded;

    private Bridge() {
    }

    /**
     * Makes sure this VM has a native library to load, extracting it when it does not, and returns the file the
     * JVM side has to load as well. The cache entry is named by the jar entry's CRC32 - already in the zip
     * central directory, so nothing hashes the payload - and the length that catches a cut-short write; naming
     * the build matters because the kind ids both sides route by come from the jar, so a library left over from
     * an earlier build would misroute calls without failing.
     */
    public static File prepare() {
        String name = "libcopperbridge.so";
        String entryName = "native/bridge/" + options.abi + "/" + name;

        File jar = jar();
        if (jar == null)
            throw new RuntimeException("cannot locate the bridge jar; pass --bridge-jar <path>");

        try (Archives archives = Archives.open(jar)) {
            Archives.Entry entry = archives.entry(entryName);

            File cached = new File(options.cacheFolder,
                    "native/bridge/" + options.abi + "/" + Long.toHexString(entry.crc()) + "/" + name);
            options.bridgeLibrary = cached;

            if (cached.isFile() && cached.length() == entry.size())
                return cached;

            cached.delete();
            Log.info("extracting the bridge native library (crc=0x"
                    + Long.toHexString(entry.crc()) + ", " + entry.size() + " bytes)");
            archives.extract(entryName, cached);
            cached.setExecutable(true, false);
            cached.setReadOnly();
            return cached;
        }
    }

    /**
     * Enters the native library for this VM. No path parameter: {@link #prepare()} left the file in
     * the options on the ART side and the JVM side was handed the same path as a system property.
     *
     * @throws RuntimeException when no library was prepared for this side, or when the library that
     *         loaded does not match the ABI the caller declared
     */
    public static synchronized void load() {
        if (loaded)
            return;

        if (options.bridgeLibrary == null)
            throw new RuntimeException("the options name no bridge library: the ART side did not"
                    + " record where it extracted one");

        System.load(options.bridgeLibrary.getAbsolutePath());
        loaded = true;

        // A value rather than a crash later: a mismatch means the wrong .so was extracted.
        int result = initNative(options.cacheFolder.getAbsolutePath(), options.abi);
        if (result != 0)
            throw new RuntimeException("failed to initialise the native bridge, code " + result
                    + " (ABI " + options.abi + ")");
    }

    /**
     * The bridge jar, or {@code null} when neither the host nor a class loader can name it:
     * {@code --bridge-jar} first, because the host knows where the jar ended up, then this class's own
     * resource URL, which is the one answer that cannot be stale.
     */
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

    /**
     * Native: records the cache folder and returns 0 when the library matches the declared ABI. The
     * one native of this class, which both VMs load, so both sides get a table for it.
     */
    @Native("jni::State::Init")
    private static native int initNative(String cacheDir, String abi);
}
