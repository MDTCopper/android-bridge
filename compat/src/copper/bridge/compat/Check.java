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
 * <p>Reads the compiled branch classes and attributes every reference to the layer the runtime classpath resolves
 * it in, in that order: the JDK, the branch's own classes, the bridge (its shared classes and lwjgl), then the
 * game jar. Only the game's references are compared at the two ends of the range; a reference no layer provides
 * is dangling and fails the check. Additions are allowed; a changed or removed declaration fails the build. A
 * class annotated with {@code @CompatRange} gets that ref as its file's ends.
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
        Map<String, String> classRanges = ClassRanges.read(bridge);
        List<ReflectionRef> reflections = ReflectionRefs.read(bridge);
        List<BridgeOverrideRef> interfaces = BridgeOverrideRefs.read(bridge);
        List<ArcReflectRef> reflect = ArcReflectRefs.read(bridge);
        String mode = options.getOrDefault("mode", "jar").trim();

        List<String> issues;
        if ("code".equals(mode)) {
            issues = checkCode(options, bridge, refs, reflections, interfaces, reflect);
        } else if ("jar".equals(mode)) {
            issues = checkJar(args, options, branch, bridge, refs, reflections, interfaces, reflect, classRanges);
        } else {
            throw new IllegalArgumentException("unknown --mode " + mode + " (jar or code)");
        }

        System.out.println(branch + " (" + mode + "): " + refs.size() + " reference(s), "
                + reflections.size() + " direct reflection target(s), " + interfaces.stream().filter(r -> r.annotated).count()
                + " bridge override method(s), " + reflect.size() + " arc Reflect target(s)");
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
                                         Path bridge, List<ClassRef> refs, List<ReflectionRef> reflections,
                                         List<BridgeOverrideRef> interfaces, List<ArcReflectRef> reflect,
                                         Map<String, String> classRanges) {
        CheckConfig config = CheckConfig.load(Paths.get(required(options, "properties")), branch);
        List<String> issues = new ArrayList<>();
        System.out.println("  total   " + refs.size() + " reference(s), "
                + reflections.size() + " reflection target(s), " + interfaces.stream().filter(r -> r.annotated).count()
                + " bridge override method(s), " + reflect.size() + " arc Reflect target(s)");

        Range branchRange = new Range(config.floor, config.ceiling);
        Map<String, Range> overrides = new HashMap<>();
        Set<String> keys = overrideKeys(refs, reflections, interfaces);
        keys.addAll(classRanges.values());
        for (String key : keys) {
            String[] ends = key.split("\\|", -1);
            overrides.put(key, new Range(
                    "default".equals(ends[0]) ? config.floor : ends[0],
                    "default".equals(ends[1]) ? config.ceiling : ends[1]));
        }

        try (RefJars jars = new RefJars()) {
            for (String spec : repeated(args, "jar")) {
                int split = spec.indexOf('=');
                jars.add(spec.substring(0, split), Paths.get(spec.substring(split + 1)));
            }

            System.out.println("  floor   " + config.floor);
            System.out.println("  ceiling " + config.ceiling);

            ClassOrigins origins = origins(args, options, bridge, jars);
            if (origins == null)
                throw new IllegalArgumentException("jar mode needs --epoch and --bridge-classpath: a reference is "
                        + "only the game's when the runtime classpath resolves it there");
            List<ClassRef> gameRefs = gameRefs(refs, origins, issues);
            System.out.println("  game    " + gameRefs.size() + " reference(s)");

            issues.addAll(ApiComparer.compare(gameRefs, branchRange, overrides, jars));
            issues.addAll(ReflectionComparer.compareJar(reflections, branchRange, overrides, jars, origins));
            issues.addAll(BridgeOverrideComparer.compareJar(interfaces, branchRange, overrides, jars, bridge, origins));
            issues.addAll(ImplementationComparer.compareJar(branchRange, overrides, jars, bridge, classRanges));
            issues.addAll(ArcReflectComparer.compareJar(reflect, branchRange, jars));
            return issues;
        }
    }

    private static List<String> checkCode(Map<String, String> options, Path bridge, List<ClassRef> refs,
                                          List<ReflectionRef> reflections, List<BridgeOverrideRef> interfaces,
                                          List<ArcReflectRef> reflect) {
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
        for (String key : overrideKeys(refs, reflections, interfaces)) {
            String[] ends = key.split("\\|", -1);
            String floor = "default".equals(ends[0]) ? gameFloor : commit(game, ends[0]);
            String ceiling = "default".equals(ends[1]) ? gameCeiling : commit(game, ends[1]);
            overrides.put(key, new Range(floor, ceiling,
                    "default".equals(ends[0]) ? arcFloor : arcAt(game, arcDir, merged, floor),
                    "default".equals(ends[1]) ? arcCeiling : arcAt(game, arcDir, merged, ceiling)));
        }

        System.out.println("  game    " + shortCommit(gameFloor) + " .. " + shortCommit(gameCeiling));
        System.out.println("  arc     " + shortCommit(arcFloor) + " .. " + shortCommit(arcCeiling));
        List<String> issues = new ArrayList<>();
        List<ClassRef> gameRefs = gameRefs(refs, null, issues);
        System.out.println("  game    " + gameRefs.size() + " reference(s)");
        issues.addAll(SourceComparer.compare(gameRefs, branchRange, overrides, gameDir, arcDir));
        issues.addAll(ReflectionComparer.compareCode(reflections, branchRange, overrides, gameDir, arcDir, null));
        issues.addAll(BridgeOverrideComparer.compareCode(interfaces, branchRange, overrides, gameDir, arcDir,
                bridge));
        issues.addAll(ArcReflectComparer.compareCode(reflect, branchRange, gameDir, arcDir));
        return issues;
    }

    /** Every {@code @CompatRange} key the branch's references and overrides declare. */
    private static Set<String> overrideKeys(List<ClassRef> refs, List<ReflectionRef> reflections,
                                            List<BridgeOverrideRef> interfaces) {
        Set<String> keys = new LinkedHashSet<>();
        for (ClassRef ref : refs) {
            if (ref.overrideKey != null)
                keys.add(ref.overrideKey);
        }
        for (ReflectionRef ref : reflections) {
            if (ref.overrideKey != null)
                keys.add(ref.overrideKey);
        }
        for (BridgeOverrideRef ref : interfaces) {
            if (ref.overrideKey != null)
                keys.add(ref.overrideKey);
        }
        return keys;
    }

    /**
     * The layered classpath, or null for a hand run that did not stage the epoch jar and the bridge's classes; the
     * old package-prefix rule then decides what is the game's.
     */
    private static ClassOrigins origins(String[] args, Map<String, String> options, Path branchClasses, RefJars jars) {
        String epoch = options.get("epoch");
        List<String> bridgePaths = repeatedValues(args, "bridge-classpath");
        if (epoch == null || epoch.trim().isEmpty() || bridgePaths.isEmpty())
            return null;

        List<Path> paths = new ArrayList<>();
        for (String path : bridgePaths)
            paths.add(Paths.get(path));
        return new ClassOrigins(branchClasses, paths, jars.jar(epoch.trim()));
    }

    /**
     * Keeps the references that are the game's; a reference on no runtime layer at all is reported and dropped.
     * The null-origins path is code mode's package rule, which has no runtime classpath to resolve against.
     */
    private static List<ClassRef> gameRefs(List<ClassRef> refs, ClassOrigins origins, List<String> issues) {
        List<ClassRef> game = new ArrayList<>();
        if (origins == null) {
            for (ClassRef ref : refs) {
                if (ref.isGameOrArc())
                    game.add(ref);
            }
            return game;
        }

        Set<String> dangling = new TreeSet<>();
        for (ClassRef ref : refs) {
            ClassOrigins.Layer layer = origins.of(ref.owner);
            if (layer == ClassOrigins.Layer.GAME) {
                game.add(ref);
            } else if (layer == ClassOrigins.Layer.DANGLING) {
                dangling.add(ref.owner + " (from " + ref.sourceClass + ")");
            }
        }
        for (String owner : dangling)
            issues.add("dangling reference, on no runtime classpath layer: " + owner);
        return game;
    }

    /** Every value of a key that can appear more than once, taken as-is. */
    private static List<String> repeatedValues(String[] args, String key) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--" + key))
                values.add(args[i + 1]);
        }
        return values;
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
