package copper.bridge.branch;

import copper.bridge.*;

/**
 * One supported game version epoch.
 *
 * <p>Two independent ranges, because the two release kinds are identified by different numbers and neither
 * implies the other: releases are compared as {@code number.minor} so a range reads like
 * {@code 146.0 .. 150.1}, while bleeding-edge bounds are build ids, far finer than release tags because where a
 * branch stops matching is a specific build. An unbounded end of a range is written as zero.</p>
 */
public class VersionEntry {
    /** Branch name, used as the dex suffix and the {@code copper.bridge.<name>} package. */
    public String branch;
    /** Human readable target, e.g. {@code Mindustry v150}. */
    public String target = "";
    /** The game tag this branch was compiled against, for diagnostics. */
    public String mindustryTag = "";
    /** The arc coordinate this branch was compiled against, for diagnostics. */
    public String arcVersion = "";

    /** Inclusive lower bound of the official release range, as {@code number.minor}. */
    public double minVersionNumber = 0;
    /** Inclusive upper bound of the official release range, as {@code number.minor}. */
    public double maxVersionNumber = 0;
    /** Inclusive lower bound of the bleeding-edge build range. */
    public int minBeBuild = 0;
    /** Inclusive upper bound of the bleeding-edge build range. */
    public int maxBeBuild = 0;

    /** Whether the given version falls inside the range that applies to its release kind. */
    public boolean matches(GameVersion version) {
        if (version == null)
            return false;
        if (version.isBleedingEdge())
            return withinBe(version.beBuild);
        return withinRelease(version.releaseValue());
    }

    /**
     * How far the given version is outside its applicable range, in the units of that range. Used to
     * pick the closest entry when nothing matches, and never mixes the two scales: a bleeding-edge
     * build is only ever compared against bleeding-edge bounds.
     */
    public double distance(GameVersion version) {
        if (version == null)
            return Double.MAX_VALUE;
        if (version.isBleedingEdge()) {
            int value = version.beBuild;
            if (minBeBuild > 0 && value < minBeBuild)
                return minBeBuild - value;
            if (maxBeBuild > 0 && value > maxBeBuild)
                return value - maxBeBuild;
            return 0;
        }
        double value = version.releaseValue();
        if (minVersionNumber > 0 && value < minVersionNumber)
            return minVersionNumber - value;
        if (maxVersionNumber > 0 && value > maxVersionNumber)
            return value - maxVersionNumber;
        return 0;
    }

    private boolean withinRelease(double value) {
        boolean aboveMin = minVersionNumber <= 0 || value >= minVersionNumber - 1e-9;
        boolean belowMax = maxVersionNumber <= 0 || value <= maxVersionNumber + 1e-9;
        return aboveMin && belowMax;
    }

    private boolean withinBe(int value) {
        boolean aboveMin = minBeBuild <= 0 || value >= minBeBuild;
        boolean belowMax = maxBeBuild <= 0 || value <= maxBeBuild;
        return aboveMin && belowMax;
    }

    @Override
    public String toString() {
        return branch + " [" + (target.isEmpty() ? mindustryTag : target) + "]";
    }
}
