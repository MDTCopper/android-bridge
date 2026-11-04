package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;

/**
 * The LWJGL natives carried inside the bridge jar: unpacks this device's two libraries, and names the jar path
 * LWJGL's bundled lookup has to be mapped to. This runs on the JVM side, the only side that has LWJGL and a loader
 * that can see the bridge jar; how LWJGL is pointed at it is the branch's business.
 */
public final class LwjglNatives {
    private LwjglNatives() {
    }

    /** The two LWJGL libraries the bridge needs, relative to the LWJGL architecture folder. */
    private static final String[] LWJGL_LIBRARIES = {
            "org/lwjgl/liblwjgl.so",
            "org/lwjgl/opengles/liblwjgl_opengles.so"
    };

    /**
     * Unpacks the LWJGL natives for this device out of the bridge jar {@link Bridge#jar()} names, through
     * {@link Libraries#extract}.
     *
     * @return the folder the natives were written to, or {@code null} when they could not be unpacked
     */
    public static File extract() {
        // flat, under the plain library names: this folder is what the branch hands LWJGL as a path entry, and a
        // lookup there falls back to <entry>/<lib name>, with no arch or module level in between
        File root = new File(Bridge.options.cacheFolder, "native/lwjgl");

        try (Archive jar = Archive.open(Bridge.jar())) {
            for (String name : LWJGL_LIBRARIES) {
                Archive.Entry entry = jar.entry(mapPath(name));
                String baseName = name.substring(name.lastIndexOf('/') + 1);
                File target = new File(root, baseName);
                Log.verbose("extracting the lwjgl native library (name=" + baseName + ", crc=0x"
                        + Long.toHexString(entry.crc()) + ", " + entry.size() + " bytes)");
                Libraries.extract(entry::read, target);
            }
        } catch (Throwable e) {
            Log.warn("GL", "failed to extract lwjgl natives; GL will not be able to load");
            Log.error(e);
            return null;
        }

        return root;
    }

    /**
     * Maps the path LWJGL looks a bundled native up at, {@code linux/<arch>/<module>/<file>}, to the copy inside the
     * bridge jar. {@link #extract()} opens its entries through this too, so the two cannot name different files.
     */
    public static String mapPath(String name) {
        return "native/lwjgl/linux/" + arch() + "/" + name;
    }

    /**
     * Translates the device's Android ABI name into the token LWJGL uses for its own native layout: the bridge and
     * the activity speak Android ABI names, while the LWJGL archives are laid out under LWJGL's own.
     */
    private static String arch() {
        if (Bridge.options.abi == null)
            return "arm64";
        switch (Bridge.options.abi) {
            case "armeabi-v7a":
                return "arm32";
            case "x86_64":
                return "x64";
            case "arm64-v8a":
            default:
                return "arm64";
        }
    }
}
