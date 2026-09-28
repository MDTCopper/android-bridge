package copper.bridge;

import copper.bridge.util.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * The game version read from {@code version.properties} inside the game jar. Its identity is entirely in
 * {@code build}: the release version for an official release, the build server's counter for a bleeding-edge
 * build. {@code number} is only the major series, and the two are not comparable.
 */
public class GameVersion {
    public Type type = Type.Official;
    public int release = 0;
    public int patch = 0;
    public int beBuild = 0;
    public String rawNumber = "0";
    public String rawBuild = "";
    public String modifier = "";
    public String display = "unknown";

    public enum Type {
        Official,
        BleedingEdge,
        CustomBuild
    }

    public static GameVersion fromProperties(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            throw new RuntimeException("failed to parse version.properties", e);
        }

        GameVersion version = new GameVersion();
        String type = properties.getProperty("type", "official").trim();
        version.rawNumber = properties.getProperty("number", "0").trim();
        version.rawBuild = properties.getProperty("build", "").trim();
        version.modifier = properties.getProperty("modifier", "").trim();
        int number = Numbers.digits(version.rawNumber, 0);
        int build = Numbers.digits(version.rawBuild, 0);

        if (type.equalsIgnoreCase("official")) {
            version.type = Type.Official;
            // the release identity is `build`; `number` is only the major series, deliberately not used here
            double release = Numbers.version(version.rawBuild);
            if (release <= 0) {
                // Mindustry writes "custom build" when buildversion was never set, which is no identity
                version.type = Type.CustomBuild;
                version.release = number;
                version.display = version.rawNumber;
            } else {
                version.release = (int)release;
                version.patch = (int)Math.round((release - version.release) * 10);
                version.display = version.rawBuild;
            }
        } else if (type.equalsIgnoreCase("bleeding-edge")) {
            version.type = Type.BleedingEdge;
            // the build id is the identity of a bleeding-edge build, not the major version it was cut from
            version.beBuild = build > 0 ? build : number;
            version.release = number;
            version.display = Integer.toString(version.beBuild);
        } else {
            version.type = Type.CustomBuild;
            version.release = number;
            version.patch = build;
            version.display = version.rawNumber;
        }
        return version;
    }

    public static GameVersion fromJar(ZipFile zip) {
        ZipEntry entry = zip.getEntry("version.properties");
        if (entry == null)
            entry = zip.getEntry("assets/version.properties");
        if (entry == null)
            return null;

        try (InputStream in = zip.getInputStream(entry)) {
            return fromProperties(new String(Streams.readAllBytes(in), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("failed to read version.properties from " + zip.getName(), e);
        }
    }

    /** Finds the game version among the given jars; {@code null} when none of them carried a version file. */
    public static GameVersion fromJars(List<File> jars) {
        for (File jar : jars) {
            if (jar == null || !jar.isFile())
                continue;
            try (ZipFile zip = new ZipFile(jar)) {
                GameVersion version = fromJar(zip);
                if (version != null)
                    return version;
            } catch (Throwable e) {
            }
        }
        return null;
    }

    public boolean isBleedingEdge() {
        return type == Type.BleedingEdge;
    }

    /** The release number as range matching compares it, {@code 159.7} as {@code 159.7}; official builds only. */
    public double releaseValue() {
        return release + patch / 10.0;
    }

    @Override
    public String toString() {
        String identity;
        if (type == Type.BleedingEdge)
            identity = "build " + beBuild + " (cut from " + release + ")";
        else
            identity = display;
        return type.name() + " " + identity + (modifier.isEmpty() ? "" : " (" + modifier + ")");
    }
}
