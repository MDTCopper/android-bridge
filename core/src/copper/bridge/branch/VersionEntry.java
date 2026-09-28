package copper.bridge.branch;

import copper.bridge.*;

/**
 * One supported game version epoch. Two independent ranges, because the two release kinds are identified by different
 * numbers: releases compare as {@code number.minor}, so a range reads like {@code 146.0 .. 150.1}, while
 * bleeding-edge bounds are build ids, far finer, because where a branch stops matching is a specific build.
 */
public class VersionEntry {
    /** Branch name, used as the dex suffix and the {@code copper.bridge.<name>} package. */
    public String branch;
    public String target = "";
    public String mindustryTag = "";
    public String arcVersion = "";

    /** Inclusive release bounds as {@code number.minor}; zero means unbounded. */
    public double minVersionNumber = 0;
    public double maxVersionNumber = 0;
    /** Inclusive bleeding-edge build id bounds; zero means unbounded. */
    public int minBeBuild = 0;
    public int maxBeBuild = 0;

    public boolean matches(GameVersion version) {
        if (version == null)
            return false;
        if (version.isBleedingEdge())
            return withinBe(version.beBuild);
        return withinRelease(version.releaseValue());
    }

    /** How far the version is outside its applicable range, in the units of that range. Never mixes the scales: a
     *  bleeding-edge build is only compared against bleeding-edge bounds. */
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
