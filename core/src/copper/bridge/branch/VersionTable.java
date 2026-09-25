package copper.bridge.branch;

import copper.bridge.util.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 * The version table shipped inside the bridge jar.
 *
 * <p>Each {@code bridge-vN} subproject emits one block while the jar is packed, so the table describes exactly
 * the branches present in this jar, and both VMs read that same file: ART to decide which dex to extract, the
 * JVM to decide which branch class to load. There is no table object - the jar carries one, so it is read when
 * first asked rather than held as a second copy of a fact that is already a file.</p>
 */
public class VersionTable {
    /** Resource name inside the bridge jar. */
    public static final String RESOURCE = "bridge-versions.properties";

    private static List<VersionEntry> entries;

    private VersionTable() {
    }

    /**
     * The branches this jar declares, in the order the selector walks them.
     *
     * <p>Read once and kept: the table cannot change while the process runs.</p>
     */
    public static List<VersionEntry> entries() {
        if (entries == null)
            entries = load();
        return entries;
    }

    /** Whether any branch is declared at all. */
    public static boolean isEmpty() {
        return entries().isEmpty();
    }

    /** Looks up one entry by branch name, or returns {@code null}. */
    public static VersionEntry byBranch(String branch) {
        for (VersionEntry entry : entries()) {
            if (entry.branch.equals(branch))
                return entry;
        }
        return null;
    }

    /** Reads the table from the classpath. */
    private static List<VersionEntry> load() {
        try (InputStream in = VersionTable.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Log.warn("no " + RESOURCE + " found in the bridge jar");
                return new ArrayList<>();
            }
            return parse(new String(Streams.readAllBytes(in), StandardCharsets.UTF_8));
        } catch (Throwable e) {
            throw new RuntimeException("failed to read " + RESOURCE, e);
        }
    }

    /** Parses the table from a properties payload. */
    private static List<VersionEntry> parse(String text) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            throw new RuntimeException("failed to parse " + RESOURCE, e);
        }

        List<VersionEntry> found = new ArrayList<>();
        int count = Numbers.parseInt(props.getProperty("count"), 0);
        for (int i = 0; i < count; i++) {
            String prefix = "branch." + i + ".";
            String name = props.getProperty(prefix + "name");
            if (name == null)
                continue;

            VersionEntry entry = new VersionEntry();
            entry.branch = name.trim();
            entry.target = props.getProperty(prefix + "target", "").trim();
            entry.mindustryTag = props.getProperty(prefix + "mindustryTag", "").trim();
            entry.arcVersion = props.getProperty(prefix + "arcVersion", "").trim();
            // release ranges are written as number.minor, bleeding-edge ranges as build ids
            entry.minVersionNumber = Numbers.parseDouble(
                    first(props, prefix, "minVersionNumber", "minVersion"), 0);
            entry.maxVersionNumber = Numbers.parseDouble(
                    first(props, prefix, "maxVersionNumber", "maxVersion"), 0);
            entry.minBeBuild = Numbers.parseInt(first(props, prefix, "minBeBuild", "minVersion"), 0);
            entry.maxBeBuild = Numbers.parseInt(first(props, prefix, "maxBeBuild", "maxVersion"), 0);
            found.add(entry);
        }

        // a deterministic order makes the "closest entry" fallback reproducible
        found.sort(Comparator.comparingDouble(e -> e.minVersionNumber));
        return found;
    }

    /** Returns the first key that is present, so older tables keep loading. */
    private static String first(Properties props, String prefix, String primary, String fallback) {
        String value = props.getProperty(prefix + primary);
        return value != null ? value : props.getProperty(prefix + fallback);
    }
}
