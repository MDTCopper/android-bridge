package copper.bridge;

import copper.bridge.util.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * The game version read from {@code version.properties} inside the game jar. Its {@code number} field is only the
 * major series, so the identity is entirely in {@code build}: the release version for an official release
 * ({@code 159.7}, or {@code 146} before the minor series), the build server's counter for a bleeding-edge build
 * ({@code 27493}). Reading {@code number} as the release is wrong by construction - it derives {@code 8.1597}
 * from a 159.7 jar, which the real jars exposed - and a build id and a release version are not comparable, so
 * the two stay in separate fields.
 */
public class GameVersion {
    public Type type = Type.Official;
    /** Official release major version, e.g. the {@code 159} of 159.7. */
    public int release = 0;
    /** Official release patch version, e.g. the {@code 7} of 159.7. */
    public int patch = 0;
    /** Bleeding-edge build id, e.g. {@code 27493}; zero for anything else. */
    public int beBuild = 0;
    /** Raw {@code number} value, kept because custom builds are not always numeric. */
    public String rawNumber = "0";
    /** Raw {@code build} value, kept for logging. */
    public String rawBuild = "";
    /** {@code modifier} field, for example {@code release} or {@code custom build}. */
    public String modifier = "";
    /** The version string as the game itself would render it. */
    public String display = "unknown";

    /** Release type, as declared by {@code version.properties}. */
    public enum Type {
        Official,
        BleedingEdge,
        CustomBuild
    }

    /** Parses a {@code version.properties} payload. */
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
            // the release identity is `build` ("159.7", or "146" in the 7.x era); `number` is only
            // the major series and is deliberately not used here
            double release = Numbers.version(version.rawBuild);
            if (release <= 0) {
                // an official build whose buildversion was never set: Mindustry writes "custom
                // build" then, which is not an identity, so it is treated as a custom jar
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
            // the build id is the identity of a bleeding-edge build, not the major version it was
            // cut from
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

    /** Reads {@code version.properties} from an open zip file, or returns {@code null}. */
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

    /**
     * Finds the game version among the given jars. Every jar on the command line is probed, because
     * the caller may pass the game jar, the arc jar and extra jars in any order; {@code null} means
     * no jar carried a version file, as happens for hand built jars that were never packaged.
     */
    public static GameVersion fromJars(List<File> jars) {
        for (File jar : jars) {
            if (jar == null || !jar.isFile())
                continue;
            try (ZipFile zip = new ZipFile(jar)) {
                GameVersion version = fromJar(zip);
                if (version != null)
                    return version;
            } catch (Throwable e) {
                // not a readable jar: keep looking
            }
        }
        return null;
    }

    /** Whether this is a bleeding-edge build, identified by its build id. */
    public boolean isBleedingEdge() {
        return type == Type.BleedingEdge;
    }

    /**
     * The official release number used for range matching: {@code 159.7} is compared as
     * {@code 159.7}. Only meaningful for official builds, the only ones that carry a release
     * version at all.
     */
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
