package copper.bridge.branch;

import copper.bridge.*;
import copper.bridge.util.*;

/**
 * Picks the version branch that matches a detected game version.
 *
 * <p>Exact matches win; when nothing matches - an unsupported bleeding edge build, a range gap, or a
 * jar without a version file - the closest entry is picked anyway and the caller is told it was a
 * guess, so the failure surfaces as a log warning, not a hard error inside a class loader.</p>
 */
public class BranchResolver {

    /** The outcome of a resolution. */
    public static class Result {
        public final VersionEntry entry;
        public final String branch;
        /** Whether the entry matched exactly; {@code false} means it was merely the closest. */
        public final boolean exact;
        /** Why the resolution is only a guess, or {@code null} when it is exact. */
        public final String note;

        Result(VersionEntry entry, boolean exact, String note) {
            this.entry = entry;
            this.branch = entry == null ? null : entry.branch;
            this.exact = exact;
            this.note = note;
        }
    }

    /**
     * Resolves a branch.
     *
     * @throws RuntimeException when the table is empty, because a jar without branches cannot run
     *                          a game at all
     */
    public static Result resolve(GameVersion version, String forcedBranch) {
        if (VersionTable.isEmpty())
            throw new RuntimeException("the bridge jar declares no version branch");

        if (forcedBranch != null) {
            VersionEntry entry = VersionTable.byBranch(forcedBranch);
            if (entry == null)
                throw new RuntimeException("unknown version branch: " + forcedBranch);
            return new Result(entry, true, null);
        }

        if (version != null) {
            for (VersionEntry entry : VersionTable.entries()) {
                if (entry.matches(version))
                    return new Result(entry, true, null);
            }
        }

        VersionEntry closest = null;
        double best = Double.MAX_VALUE;
        for (VersionEntry entry : VersionTable.entries()) {
            double distance = entry.distance(version);
            if (distance < best) {
                best = distance;
                closest = entry;
            }
        }

        String note;
        if (version == null) {
            note = "no version.properties found in the supplied jars, falling back to the closest branch";
        } else {
            note = "game version " + version + " is outside every declared range, falling back to the closest branch";
        }
        Log.warn(note);
        return new Result(closest, false, note);
    }
}
