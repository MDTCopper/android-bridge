package copper.bridge.art;

import android.content.res.Resources;
import android.os.Build;
import android.util.DisplayMetrics;
import java.util.*;

/**
 * The device facts the ART side resolves for the JVM side, which as a separate VM with no {@code android.*} in scope
 * cannot answer them for itself. Nothing on the JVM side loads this class, which is why it names Android types
 * directly instead of through {@code Class.forName}.
 */
public class Device {
    private Device() {
    }

    public static String abi() {
        String abi = cpuAbi();
        if (abi != null && !abi.isEmpty())
            return abi;
        return abiFromArch(arch());
    }

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

    /** The CPU architecture as the game expects it in {@code os.arch}, which is not the ABI's spelling. */
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

    public static int apiLevel() {
        return Build.VERSION.SDK_INT;
    }

    /** The display density, or 1 when it cannot be read: the system resources carry the default's density. */
    public static float density() {
        float density = metrics().density;
        return density > 0f ? density : 1f;
    }

    public static float xdpi() {
        return metrics().xdpi;
    }

    public static float ydpi() {
        return metrics().ydpi;
    }

    private static DisplayMetrics metrics() {
        return Resources.getSystem().getDisplayMetrics();
    }

    /** The {@code lib/<arch>} directory names a JRE may use here; the spelling differs, so all are tried. */
    public static List<String> archCandidates() {
        List<String> candidates = new ArrayList<>();
        String arch = System.getProperty("os.arch", "");
        String abi = cpuAbi();
        for (String candidate : new String[] {arch, abi}) {
            if (candidate == null || candidate.isEmpty() || candidates.contains(candidate))
                continue;
            candidates.add(candidate);
        }
        for (String extra : new String[] {"aarch64", "arm", "arm64", "amd64", "x86_64", "i386", "i486", "i586"}) {
            if (!candidates.contains(extra))
                candidates.add(extra);
        }
        return candidates;
    }

    /** The ABI this process runs as: {@code SUPPORTED_ABIS}, not the deprecated {@code Build.CPU_ABI}. */
    private static String cpuAbi() {
        return Build.SUPPORTED_ABIS[0];
    }
}
