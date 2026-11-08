package copper.bridge.compat.config;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * One branch's API check configuration, read from the repository's gradle.properties.
 *
 * <p>Keeping it there means the branch's range has a single source: the build does not pin a second copy in a
 * generated file. The ends are game commits of real bleeding-edge builds, not release tags, because the range is
 * owned by build ids.
 */
public final class CheckConfig {
    public final String branch;
    public final String mindustryFloor;
    public final String mindustryCeiling;
    public final String arcFloor;
    public final String arcCeiling;
    /** True when that end's arc lives inside the game tree; the arc commit is then a game commit. */
    public final boolean arcFloorMerged;
    public final boolean arcCeilingMerged;

    private CheckConfig(String branch, String mindustryFloor, String mindustryCeiling,
                        String arcFloor, String arcCeiling, boolean arcFloorMerged, boolean arcCeilingMerged) {
        this.branch = branch;
        this.mindustryFloor = mindustryFloor;
        this.mindustryCeiling = mindustryCeiling;
        this.arcFloor = arcFloor;
        this.arcCeiling = arcCeiling;
        this.arcFloorMerged = arcFloorMerged;
        this.arcCeilingMerged = arcCeilingMerged;
    }

    public static CheckConfig load(Path properties, String branch) {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CheckConfig(branch,
                required(values, branch + ".compatMindustryFloor"),
                required(values, branch + ".compatMindustryCeiling"),
                required(values, branch + ".compatArcFloor"),
                required(values, branch + ".compatArcCeiling"),
                Boolean.parseBoolean(values.getProperty(branch + ".compatArcFloorMerged", "false").trim()),
                Boolean.parseBoolean(values.getProperty(branch + ".compatArcCeilingMerged", "false").trim()));
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("missing " + key + " in gradle.properties");
        return value.trim();
    }
}
