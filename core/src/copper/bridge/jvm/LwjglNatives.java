package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;

/**
 * Unpacks the LWJGL natives carried inside the bridge jar.
 *
 * <p>LWJGL finds libraries by searching the directories it is told about and knows nothing about the
 * layout inside the jar, so the files are written out first and the caller points LWJGL at the
 * result ({@code Configuration.LIBRARY_PATH}). This runs on the JVM side, the only side that has
 * LWJGL and a loader that can see the bridge jar; what LWJGL is told is the branch's business.</p>
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
     * Unpacks the LWJGL natives for this device out of the bridge jar, read through this class's own class
     * loader rather than out of the jar file by name: a loader that put the bridge on its path without saying
     * where the jar is can still answer this, and the archive's location is not something this side has to know.
     *
     * @return the extracted {@code linux/<arch>} folder, or {@code null} when the bridge jar does not carry
     * LWJGL natives at all
     */
    public static File extract() {
        String arch = arch(Bridge.options.abi);
        File root = new File(Bridge.options.cacheFolder, "native/lwjgl/linux/" + arch);

        try (Archives jar = Archives.open(Bridge.options.bridgeJar)) {
            for (String name : LWJGL_LIBRARIES) {
                String resource = "native/lwjgl/linux/" + arch + "/" + name;
                Archives.Entry entry = jar.entry(resource);
                File target = new File(root, name);
                Log.info("extracting the lwjgl native library (crc=0x"
                        + Long.toHexString(entry.crc()) + ", " + entry.size() + " bytes)");
                jar.extractLibrary(resource, target);
            }
        } catch (Throwable e) {
            Log.warn("GL", "failed to extract lwjgl natives; GL will not be able to load");
            return null;
        }

        if (!new File(root, LWJGL_LIBRARIES[0]).isFile()) {
            Log.warn("GL", "no lwjgl natives were extracted; GL will not be able to load");
            return null;
        }
        return root;
    }

    /**
     * Translates an Android ABI name into the token LWJGL uses for its own native layout: the bridge
     * and the activity speak Android ABI names, while the LWJGL archives are laid out under LWJGL's
     * own.
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
