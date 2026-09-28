package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;

/**
 * Unpacks the LWJGL natives carried inside the bridge jar. This runs on the JVM side, the only side that has
 * LWJGL and a loader that can see the bridge jar; what LWJGL is told is the branch's business.
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
     * @return the extracted {@code linux/<arch>} folder, or {@code null} when the natives could not be unpacked
     */
    public static File extract() {
        String arch = arch(Bridge.options.abi);
        File root = new File(Bridge.options.cacheFolder, "native/lwjgl/linux/" + arch);

        try (Archive jar = Archive.open(Bridge.jar())) {
            for (String name : LWJGL_LIBRARIES) {
                Archive.Entry entry = jar.entry("native/lwjgl/linux/" + arch + "/" + name);
                File target = new File(root, name);
                Log.verbose("extracting the lwjgl native library (name=" + name + ", crc=0x"
                        + Long.toHexString(entry.crc()) + ", " + entry.size() + " bytes)");
                Libraries.extract(entry::read, target);
            }
        } catch (Throwable e) {
            Log.warn("GL", "failed to extract lwjgl natives; GL will not be able to load");
            Log.error(e);
            return null;
        }

        if (!new File(root, LWJGL_LIBRARIES[0]).isFile()) {
            Log.warn("GL", "no lwjgl natives were extracted; GL will not be able to load");
            return null;
        }
        return root;
    }

    /**
     * Translates an Android ABI name into the token LWJGL uses for its own native layout: the bridge and the
     * activity speak Android ABI names, while the LWJGL archives are laid out under LWJGL's own.
     */
    private static String arch(String abi) {
        if (abi == null)
            return "arm64";
        switch (abi) {
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
