package copper.bridge.compat.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** One branch's API check configuration: the two jar refs to compare against. */
public final class CheckConfig {
    public final String branch;
    public final String floor;
    public final String ceiling;

    private CheckConfig(String branch, String floor, String ceiling) {
        this.branch = branch;
        this.floor = floor;
        this.ceiling = ceiling;
    }

    public static CheckConfig load(Path properties, String branch) {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(properties, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CheckConfig(branch,
                required(values, branch + ".compatFloor"),
                required(values, branch + ".compatCeiling"));
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("missing " + key + " in gradle.properties");
        return value.trim();
    }
}
