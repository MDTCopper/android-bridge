package copper.bridge.art;

import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import java.util.*;

/**
 * The device facts the ART side resolves for the JVM side.
 *
 * <p>The JVM is a separate VM with its own system properties and no {@code android.*} in scope, so it
 * cannot answer these for itself: they are read while the launcher is still parsing arguments, before
 * any activity exists, and recorded in {@link BridgeOptions}. Nothing on the JVM side loads this
 * class, which is why it names Android types directly rather than through {@code Class.forName}.</p>
 */
public class Device {
    private Device() {
    }

    /** The device ABI, for example {@code arm64-v8a}. */
    public static String abi() {
        String abi = cpuAbi();
        if (abi != null && !abi.isEmpty())
            return abi;
        return abiFromArch(arch());
    }

    /** Maps a JVM architecture name onto an Android ABI. */
    public static String abiFromArch(String arch) {
        String normalized = arch == null ? "" : arch.toLowerCase();
        if (normalized.contains("aarch64") || normalized.contains("arm64"))
            return "arm64-v8a";
        if (normalized.startsWith("arm"))
            return "armeabi-v7a";
        if (normalized.contains("x86_64") || normalized.contains("amd64"))
            return "x86_64";
        if (normalized.contains("x86") || normalized.contains("i386") || normalized.contains("i686"))
            return "x86";
        return "arm64-v8a";
    }

    /** The CPU architecture as the game expects it in {@code os.arch}. */
    public static String arch() {
        String abi = cpuAbi();
        if (abi == null)
            abi = System.getProperty("os.arch", "");
        String normalized = abi.toLowerCase();
        if (normalized.contains("arm64") || normalized.contains("aarch64"))
            return "aarch64";
        if (normalized.contains("armeabi") || normalized.startsWith("arm"))
            return "armv7l";
        if (normalized.contains("x86_64"))
            return "x86_64";
        if (normalized.contains("x86"))
            return "i386";
        return normalized.isEmpty() ? "aarch64" : normalized;
    }

    /** The Android API level, injected as {@code os.version}. */
    public static int apiLevel() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * The display density, or 1 when it cannot be read. Read from the system resources rather than
     * from an activity, because this runs before any activity exists; those resources already carry
     * the default display's density.
     */
    public static float density() {
        float density = metrics().density;
        return density > 0f ? density : 1f;
    }

    /** Physical pixels per inch on the x axis, or 0 when the device does not report it. */
    public static float xdpi() {
        return metrics().xdpi;
    }

    /** Physical pixels per inch on the y axis, or 0 when the device does not report it. */
    public static float ydpi() {
        return metrics().ydpi;
    }

    /** The metrics of the default display, available before any activity is. */
    private static DisplayMetrics metrics() {
        return Resources.getSystem().getDisplayMetrics();
    }

    /**
     * The {@code lib/<arch>} directory names a JRE may use on this device: the exact spelling differs
     * between distributions, so every plausible one is tried in turn instead of guessing once.
     */
    public static List<String> archCandidates() {
        List<String> candidates = new ArrayList<>();
        String arch = System.getProperty("os.arch", "");
        String abi = cpuAbi();
        for (String candidate : new String[]{arch, abi}) {
            if (candidate == null || candidate.isEmpty() || candidates.contains(candidate))
                continue;
            candidates.add(candidate);
        }
        // the names OpenJDK derived Android builds use
        for (String extra : new String[]{"aarch64", "arm", "arm64", "amd64", "x86_64", "i386", "i486", "i586"}) {
            if (!candidates.contains(extra))
                candidates.add(extra);
        }
        return candidates;
    }

    /**
     * The ABI this process runs as, as Android reports it: {@code SUPPORTED_ABIS}, not the deprecated
     * {@code Build.CPU_ABI}, because the callers need the ABI whose {@code libcopperbridge.so} can be
     * loaded into this process - not everything the device can run.
     */
    private static String cpuAbi() {
        return Build.SUPPORTED_ABIS[0];
    }
}
