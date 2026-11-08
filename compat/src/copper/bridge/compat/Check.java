package copper.bridge.compat;

import copper.bridge.compat.check.*;
import copper.bridge.compat.config.*;
import copper.bridge.compat.refs.*;
import copper.bridge.compat.sources.*;

import java.nio.file.*;
import java.util.*;

/**
 * Build-time API check: the branch compiles against one epoch but runs on every version of its range, so a
 * member whose declaration changed inside the range still compiles and only fails on the device.
 *
 * <p>Reads the compiled branch classes, keeps the references into arc and mindustry, and compares their
 * declarations at the two ends of the branch's bleeding-edge range. Additions are allowed; a changed or removed
 * declaration fails the build. A class annotated with {@code @CompatFloor} gets that ref as its file's floor.
 * The game and arc are never built: the sources come from the two git clones.
 */
public final class Check {

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        CheckConfig config = CheckConfig.load(Paths.get(required(options, "properties")), branch);
        Path bridge = Paths.get(required(options, "bridge"));
        Path gameClone = Paths.get(required(options, "mindustry"));
        Path arcClone = Paths.get(required(options, "arc"));

        List<ClassRef> refs = ClassRefs.read(bridge);
        Range branchRange = new Range(config.mindustryFloor, config.mindustryCeiling,
                config.arcFloor, config.arcCeiling, config.arcFloorMerged, config.arcCeilingMerged);
        Map<String, Range> overrides = new HashMap<>();
        for (ClassRef ref : refs) {
            if (ref.overrideKey != null && !overrides.containsKey(ref.overrideKey)) {
                String[] ends = ref.overrideKey.split("\\|", -1);
                overrides.put(ref.overrideKey, resolve(ends[0], ends[1], config, branchRange, gameClone, arcClone));
            }
        }

        List<String> issues = ApiComparer.compare(refs, branchRange, overrides, gameClone, arcClone);
        System.out.println(branch + ": checking " + refs.size() + " referenced arc/mindustry member(s)");
        System.out.println("  arc       " + shortCommit(config.arcFloor) + " .. " + shortCommit(config.arcCeiling));
        System.out.println("  mindustry " + shortCommit(config.mindustryFloor) + " .. "
                + shortCommit(config.mindustryCeiling));
        for (String issue : issues)
            System.out.println("  " + issue);
        if (issues.isEmpty()) {
            System.out.println("API check passed");
        } else {
            System.out.println("API check FAILED: " + issues.size() + " incompatible reference(s)");
            System.exit(1);
        }
    }

    /** Resolves a {@code @CompatRange} pair; {@code "default"} keeps that end from the branch. */
    private static Range resolve(String floorRef, String ceilingRef, CheckConfig config, Range branch,
                                 Path gameClone, Path arcClone) {
        Git game = new Git(gameClone.toString());
        String gameFloor = "default".equals(floorRef) ? branch.gameFloor : commit(game, floorRef);
        String gameCeiling = "default".equals(ceilingRef) ? branch.gameCeiling : commit(game, ceilingRef);
        String arcFloor = "default".equals(floorRef)
                ? branch.arcFloor : arcAt(game, arcClone, config.arcFloorMerged, gameFloor);
        String arcCeiling = "default".equals(ceilingRef)
                ? branch.arcCeiling : arcAt(game, arcClone, config.arcCeilingMerged, gameCeiling);
        return new Range(gameFloor, gameCeiling, arcFloor, arcCeiling,
                config.arcFloorMerged, config.arcCeilingMerged);
    }

    private static String commit(Git game, String ref) {
        return game.run("rev-parse", "--verify", ref + "^{commit}").trim();
    }

    private static String arcAt(Git game, Path arcClone, boolean merged, String gameCommit) {
        if (merged)
            return gameCommit;
        String archash = null;
        for (String line : game.run("show", gameCommit + ":gradle.properties").split("\n")) {
            line = line.trim();
            if (line.startsWith("archash=")) {
                archash = line.substring("archash=".length()).trim();
                break;
            }
        }
        if (archash == null || archash.isEmpty())
            throw new IllegalStateException("no archash declared by " + gameCommit);
        return new Git(arcClone.toString()).run("rev-parse", "--verify", archash + "^{commit}").trim();
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--"))
                continue;
            String key = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--"))
                options.put(key, args[++i]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("missing --" + key);
        return value.trim();
    }

    private static String shortCommit(String commit) {
        return commit.length() > 10 ? commit.substring(0, 10) : commit;
    }
}
