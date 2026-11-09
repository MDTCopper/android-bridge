package copper.bridge.compat;

import copper.bridge.compat.check.*;
import copper.bridge.compat.config.CheckConfig;
import copper.bridge.compat.refs.*;
import copper.bridge.compat.sources.Git;
import copper.bridge.compat.sources.RefJars;

import java.nio.file.*;
import java.util.*;

/**
 * Build-time API check: the branch compiles against one epoch but runs on every version of its range, so a
 * member whose declaration changed inside the range still compiles and only fails on the older device.
 *
 * <p>Reads the compiled branch classes, keeps the references into arc and mindustry, and compares their
 * declarations at the two ends of the branch's range. Additions are allowed; a changed or removed declaration
 * fails the build. A class annotated with {@code @CompatRange} gets that ref as its file's ends.
 *
 * <p>Two modes read the ends: {@code jar} (the default) reads the staged desktop jars handed in as
 * {@code --jar <ref>=<path>}; {@code code} reads the game and arc sources at the named revisions in the clones
 * given by {@code --mindustry} and {@code --arc}, so a range can be probed without building or downloading
 * anything. The code mode resolves the arc revision from the game commit's {@code archash} unless one is named.
 */
public final class Check {

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        Path bridge = Paths.get(required(options, "bridge"));
        List<ClassRef> refs = ClassRefs.read(bridge);
        List<ReflectionRef> reflections = ReflectionRefs.read(bridge);
        String mode = options.getOrDefault("mode", "jar").trim();

        List<String> issues;
        if ("code".equals(mode)) {
            issues = checkCode(options, refs, reflections);
        } else if ("jar".equals(mode)) {
            issues = checkJar(args, options, branch, refs, reflections);
        } else {
            throw new IllegalArgumentException("unknown --mode " + mode + " (jar or code)");
        }

        System.out.println(branch + " (" + mode + "): checking " + refs.size()
                + " referenced arc/mindustry member(s), " + reflections.size() + " reflection target(s)");
        for (String issue : issues)
            System.out.println("  " + issue);
        if (issues.isEmpty()) {
            System.out.println("API check passed");
        } else {
            System.out.println("API check FAILED: " + issues.size() + " incompatible reference(s)");
            System.exit(1);
        }
    }

    private static List<String> checkJar(String[] args, Map<String, String> options, String branch,
                                         List<ClassRef> refs, List<ReflectionRef> reflections) {
        CheckConfig config = CheckConfig.load(Paths.get(required(options, "properties")), branch);
        BridgeClosure.Result closure = BridgeClosure.expand(Paths.get(required(options, "bridge")),
                bridgeDirs(args), versionOf(branch));
        List<String> issues = new ArrayList<>(closure.issues);
        List<ClassRef> allRefs = new ArrayList<>(refs);
        allRefs.addAll(closure.refs);
        List<ReflectionRef> allReflections = new ArrayList<>(reflections);
        allReflections.addAll(closure.reflections);

        Range branchRange = new Range(config.floor, config.ceiling);
        Map<String, Range> overrides = new HashMap<>();
        for (ClassRef ref : allRefs) {
            if (ref.overrideKey != null && !overrides.containsKey(ref.overrideKey)) {
                String[] ends = ref.overrideKey.split("\\|", -1);
                overrides.put(ref.overrideKey, new Range(
                        "default".equals(ends[0]) ? config.floor : ends[0],
                        "default".equals(ends[1]) ? config.ceiling : ends[1]));
            }
        }
        for (ReflectionRef ref : allReflections) {
            if (ref.overrideKey != null && !overrides.containsKey(ref.overrideKey)) {
                String[] ends = ref.overrideKey.split("\\|", -1);
                overrides.put(ref.overrideKey, new Range(
                        "default".equals(ends[0]) ? config.floor : ends[0],
                        "default".equals(ends[1]) ? config.ceiling : ends[1]));
            }
        }

        try (RefJars jars = new RefJars()) {
            for (String spec : repeated(args, "jar")) {
                int split = spec.indexOf('=');
                jars.add(spec.substring(0, split), Paths.get(spec.substring(split + 1)));
            }

            System.out.println("  floor   " + config.floor);
            System.out.println("  ceiling " + config.ceiling);
            issues.addAll(ApiComparer.compare(allRefs, branchRange, overrides, jars));
            issues.addAll(ReflectionComparer.compareJar(allReflections, branchRange, overrides, jars));
            return issues;
        }
    }

    private static List<String> checkCode(Map<String, String> options, List<ClassRef> refs,
                                          List<ReflectionRef> reflections) {
        Path gameDir = Paths.get(required(options, "mindustry"));
        Path arcDir = Paths.get(required(options, "arc"));
        boolean merged = Boolean.parseBoolean(options.getOrDefault("merged", "false"));
        Git game = new Git(gameDir.toString());
        Git arc = new Git(arcDir.toString());

        String gameFloor = commit(game, required(options, "game-floor"));
        String gameCeiling = commit(game, required(options, "game-ceiling"));
        String arcFloor = options.containsKey("arc-floor")
                ? commit(arc, options.get("arc-floor"))
                : arcAt(game, arcDir, merged, gameFloor);
        String arcCeiling = options.containsKey("arc-ceiling")
                ? commit(arc, options.get("arc-ceiling"))
                : arcAt(game, arcDir, merged, gameCeiling);
        Range branchRange = new Range(gameFloor, gameCeiling, arcFloor, arcCeiling);

        Map<String, Range> overrides = new HashMap<>();
        for (ClassRef ref : refs) {
            if (ref.overrideKey != null && !overrides.containsKey(ref.overrideKey)) {
                String[] ends = ref.overrideKey.split("\\|", -1);
                String floor = "default".equals(ends[0]) ? gameFloor : commit(game, ends[0]);
                String ceiling = "default".equals(ends[1]) ? gameCeiling : commit(game, ends[1]);
                overrides.put(ref.overrideKey, new Range(floor, ceiling,
                        "default".equals(ends[0]) ? arcFloor : arcAt(game, arcDir, merged, floor),
                        "default".equals(ends[1]) ? arcCeiling : arcAt(game, arcDir, merged, ceiling)));
            }
        }
        for (ReflectionRef ref : reflections) {
            if (ref.overrideKey != null && !overrides.containsKey(ref.overrideKey)) {
                String[] ends = ref.overrideKey.split("\\|", -1);
                String floor = "default".equals(ends[0]) ? gameFloor : commit(game, ends[0]);
                String ceiling = "default".equals(ends[1]) ? gameCeiling : commit(game, ends[1]);
                overrides.put(ref.overrideKey, new Range(floor, ceiling,
                        "default".equals(ends[0]) ? arcFloor : arcAt(game, arcDir, merged, floor),
                        "default".equals(ends[1]) ? arcCeiling : arcAt(game, arcDir, merged, ceiling)));
            }
        }

        System.out.println("  game    " + shortCommit(gameFloor) + " .. " + shortCommit(gameCeiling));
        System.out.println("  arc     " + shortCommit(arcFloor) + " .. " + shortCommit(arcCeiling));
        List<String> issues = new ArrayList<>(SourceComparer.compare(refs, branchRange, overrides, gameDir, arcDir));
        issues.addAll(ReflectionComparer.compareCode(reflections, branchRange, overrides, gameDir, arcDir));
        return issues;
    }

    private static String commit(Git git, String revision) {
        try {
            return git.run("rev-parse", "--verify", revision + "^{commit}").trim();
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("cannot resolve revision " + revision, e);
        }
    }

    private static String arcAt(Git game, Path arcDir, boolean merged, String gameCommit) {
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
            throw new IllegalStateException("no archash declared by " + gameCommit
                    + "; pass --arc-floor and --arc-ceiling");
        return commit(new Git(arcDir.toString()), archash);
    }

    /** The classes directory of every lower branch, keyed by branch number. */
    private static Map<Integer, Path> bridgeDirs(String[] args) {
        Map<Integer, Path> dirs = new HashMap<>();
        for (String spec : repeated(args, "bridge-dir")) {
            int split = spec.indexOf('=');
            dirs.put(versionOf(spec.substring(0, split)), Paths.get(spec.substring(split + 1)));
        }
        return dirs;
    }

    private static int versionOf(String branch) {
        String digits = branch.replaceAll("\\D+", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
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

    /** Every value of a key that can appear more than once, such as {@code --jar}. */
    private static List<String> repeated(String[] args, String key) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (!args[i].equals("--" + key))
                continue;
            String value = args[i + 1];
            if (value.indexOf('=') <= 0)
                throw new IllegalArgumentException("--" + key + " wants <ref>=<path>: " + value);
            values.add(value);
        }
        return values;
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
