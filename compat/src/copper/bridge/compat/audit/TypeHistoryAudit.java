package copper.bridge.compat.audit;

import copper.bridge.compat.refs.ClassRef;
import copper.bridge.compat.refs.ClassRefs;
import copper.bridge.compat.refs.HandledRefs;
import copper.bridge.compat.refs.ReflectionRef;
import copper.bridge.compat.refs.ReflectionRefs;
import copper.bridge.compat.refs.TypeDep;
import copper.bridge.compat.refs.TypeDeps;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Reports the game and arc types a branch references - directly or through reflection - whose source appears,
 * disappears and appears again across the commits of its window.
 *
 * <p>Reads source trees with one {@code git log} per repository; a per-commit {@code git ls-tree} would spawn
 * thousands of processes. A build-id window has no commit to resolve and is not covered.
 */
public final class TypeHistoryAudit {

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        Path classes = Paths.get(required(options, "classes"));
        Path mindustry = Paths.get(required(options, "mindustry"));
        Path arc = Paths.get(required(options, "arc"));
        Path jars = options.containsKey("jars") ? Paths.get(options.get("jars")) : null;
        Path properties = options.containsKey("properties") ? Paths.get(options.get("properties")) : null;
        String from = required(options, "from");
        String to = options.getOrDefault("to", "").trim();
        if (to.isEmpty())
            to = "HEAD";

        Set<String> direct = new TreeSet<>();
        for (ClassRef ref : ClassRefs.read(classes)) {
            if (isGameOrArc(ref.owner))
                direct.add(ref.owner);
        }
        ReflectionRefs.Result reflection = ReflectionRefs.read(classes);
        Set<String> reflected = new TreeSet<>();
        for (ReflectionRef ref : reflection.refs) {
            if (isGameOrArc(ref.owner))
                reflected.add(ref.owner);
        }
        Set<String> typed = new TreeSet<>();
        for (TypeDep dep : TypeDeps.read(classes)) {
            if (isGameOrArc(dep.owner))
                typed.add(dep.owner);
        }
        if (!reflection.issues.isEmpty()) {
            for (String issue : reflection.issues)
                System.out.println(branch + ": " + issue);
            System.exit(1);
        }

        Set<String> all = union(union(direct, reflected), typed);
        Set<String> unaudited = new TreeSet<>();
        List<String> issues = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        Map<String, String> sequences = new HashMap<>();
        int commits = 0;
        for (Path clone : new Path[]{mindustry, arc}) {
            List<String> owners = new ArrayList<>();
            for (String owner : all) {
                if ((isArc(owner) ? arc : mindustry).equals(clone))
                    owners.add(owner);
            }
            if (owners.isEmpty())
                continue;
            Resolved resolved = resolve(clone, mindustry, jars, properties, from, to, issues);
            if (resolved == null) {
                unaudited.add(clone.equals(arc) ? "arc" : "game");
                continue;
            }
            if (!resolved.from.equals(from) || !resolved.to.equals(to))
                fallbacks.add((clone.equals(arc) ? "arc" : "game") + " " + from + ".." + to + " -> "
                        + resolved.from.substring(0, Math.min(10, resolved.from.length())) + ".."
                        + resolved.to.substring(0, Math.min(10, resolved.to.length()))
                        + " by version metadata");
            History history = history(clone, resolved.from, resolved.to, owners);
            sequences.putAll(history.sequences);
            commits = Math.max(commits, history.commits);
        }

        List<String> findings = new ArrayList<>();
        HandledRefs handled = HandledRefs.read(classes);
        for (String owner : all) {
            if (handled.handles(owner))
                continue;
            String sequence = sequences.get(owner);
            if (sequence == null)
                continue;
            int changes = 0;
            for (int i = 1; i < sequence.length(); i++) {
                if (sequence.charAt(i) != sequence.charAt(i - 1))
                    changes++;
            }
            if (changes > 1)
                findings.add((reflected.contains(owner) ? "reflection " : direct.contains(owner) ? "direct " : "type ")
                        + owner + " " + sequence);
        }

        for (String finding : findings)
            System.out.println(branch + ": " + finding);
        for (String fallback : fallbacks)
            System.out.println(branch + ": " + fallback);
        for (String issue : issues)
            System.out.println(branch + ": " + issue);
        for (String repo : unaudited)
            System.out.println(branch + ": no " + repo + " source for " + from + ".." + to
                    + "; its types were not audited");
        System.out.println(branch + ": " + all.size() + " referenced type(s), " + findings.size()
                + " transient type(s) across " + commits + " commit(s)");
        if (!issues.isEmpty())
            System.exit(1);
    }

    static final class Resolved {
        final String from;
        final String to;

        Resolved(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    /**
     * Resolves both revisions. Arc has no tags before v93, so its window is anchored to the game tag commit time and
     * the nearest earlier Arc commit, reported so the approximation is never silent.
     */
    static Resolved resolve(Path clone, Path game, Path jars, Path properties, String from, String to,
                            List<String> issues)
            throws IOException, InterruptedException {
        if (refs(clone, from, to))
            return new Resolved(from, to);

        String gameFrom = gameRevision(game, jars, properties, from, issues);
        String gameTo = gameRevision(game, jars, properties, to, issues);
        if (gameFrom == null || gameTo == null)
            return null;
        if (clone.equals(game))
            return new Resolved(gameFrom, gameTo);

        String arcFrom = revision(clone, from) ? from : before(clone, gameTime(game, gameFrom));
        String arcTo = revision(clone, to) ? to : before(clone, gameTime(game, gameTo));
        return arcFrom == null || arcTo == null ? null : new Resolved(arcFrom, arcTo);
    }

    private static String gameRevision(Path game, Path jars, Path properties, String ref, List<String> issues)
            throws IOException, InterruptedException {
        if (!ref.matches("\\d+") && revision(game, ref))
            return ref;
        if (jars == null) {
            issues.add("no --jars path for " + ref);
            return null;
        }
        return VersionInfo.commit(jars, ref, game, properties, issues);
    }

    private static boolean revision(Path clone, String ref) throws IOException, InterruptedException {
        if (ref.matches("\\d+"))
            return false;
        return outputStatus(clone, "rev-parse", "--verify", "--quiet", ref + "^{commit}") == 0;
    }

    private static long gameTime(Path game, String ref) throws IOException, InterruptedException {
        String value = output(game, "show", "-s", "--format=%ct", ref).get(0).trim();
        return Long.parseLong(value);
    }

    private static String before(Path clone, long epoch) throws IOException, InterruptedException {
        List<String> revisions = output(clone, "rev-list", "-1", "--before=" + (epoch - 1), "HEAD");
        return revisions.isEmpty() ? null : revisions.get(0).trim();
    }

    private static final class History {
        final Map<String, String> sequences;
        final int commits;

        History(Map<String, String> sequences, int commits) {
            this.sequences = sequences;
            this.commits = commits;
        }
    }

    /**
     * Rebuilds every owner's presence from the tree at {@code from} and one name-status log. Matches suffix once per
     * changed path, not once per owner per commit.
     */
    private static History history(Path clone, String from, String to, List<String> owners)
            throws IOException, InterruptedException {
        Set<String> paths = new HashSet<>(output(clone, "ls-tree", "-r", "--name-only", from));
        Map<String, List<Integer>> matching = new HashMap<>();
        List<String> suffixes = new ArrayList<>();
        for (int i = 0; i < owners.size(); i++) {
            String owner = owners.get(i);
            String base = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
            String suffix = "/" + base + ".java";
            suffixes.add(suffix);
            matching.computeIfAbsent(suffix, key -> new ArrayList<>()).add(i);
        }

        int[] counts = new int[owners.size()];
        for (String path : paths) {
            for (Map.Entry<String, List<Integer>> entry : matching.entrySet()) {
                if (path.endsWith(entry.getKey())) {
                    for (int index : entry.getValue())
                        counts[index]++;
                    break;
                }
            }
        }

        StringBuilder[] sequences = new StringBuilder[owners.size()];
        for (int i = 0; i < owners.size(); i++) {
            sequences[i] = new StringBuilder();
            sequences[i].append(counts[i] > 0 ? '1' : '0');
        }

        List<String> lines = output(clone, "log", "--reverse", "--format=@@%H",
                "--name-status", "--diff-filter=ADR", from + ".." + to);
        boolean started = false;
        List<String[]> changes = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("@@")) {
                if (started) {
                    apply(changes, paths, counts, matching);
                    append(sequences, counts);
                }
                started = true;
                changes.clear();
                continue;
            }
            if (line.isEmpty())
                continue;
            String[] parts = line.split("\t");
            if (parts.length >= 2)
                changes.add(parts);
        }
        if (started) {
            apply(changes, paths, counts, matching);
            append(sequences, counts);
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < owners.size(); i++)
            result.put(owners.get(i), sequences[i].toString());
        int total = Integer.parseInt(output(clone, "rev-list", "--count", from + ".." + to).get(0).trim());
        return new History(result, total);
    }

    private static void append(StringBuilder[] sequences, int[] counts) {
        for (int i = 0; i < sequences.length; i++)
            sequences[i].append(counts[i] > 0 ? '1' : '0');
    }

    private static void apply(List<String[]> changes, Set<String> paths, int[] counts,
                              Map<String, List<Integer>> matching) {
        for (String[] change : changes) {
            String status = change[0];
            if (status.startsWith("R") && change.length >= 3) {
                remove(change[1], paths, counts, matching);
                add(change[2], paths, counts, matching);
            } else if (status.startsWith("A")) {
                add(change[1], paths, counts, matching);
            } else if (status.startsWith("D")) {
                remove(change[1], paths, counts, matching);
            }
        }
    }

    private static void add(String path, Set<String> paths, int[] counts, Map<String, List<Integer>> matching) {
        if (!paths.add(path))
            return;
        for (Map.Entry<String, List<Integer>> entry : matching.entrySet()) {
            if (path.endsWith(entry.getKey())) {
                for (int index : entry.getValue())
                    counts[index]++;
                return;
            }
        }
    }

    private static void remove(String path, Set<String> paths, int[] counts, Map<String, List<Integer>> matching) {
        if (!paths.remove(path))
            return;
        for (Map.Entry<String, List<Integer>> entry : matching.entrySet()) {
            if (path.endsWith(entry.getKey())) {
                for (int index : entry.getValue())
                    counts[index]--;
                return;
            }
        }
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> all = new TreeSet<>(first);
        all.addAll(second);
        return all;
    }

    /** Whether the clone has both revisions. Arc predates the split with its own tags, so a window can predate it. */
    private static boolean refs(Path clone, String from, String to) throws IOException, InterruptedException {
        return revision(clone, from) && revision(clone, to);
    }

    private static List<String> output(Path clone, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(clone.toString());
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null)
                lines.add(line);
        }
        if (process.waitFor() != 0)
            throw new IOException("git " + String.join(" ", args) + " failed in " + clone);
        return lines;
    }

    /** Runs a probe command and returns its exit status without treating a non-zero status as an error. */
    private static int outputStatus(Path clone, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(clone.toString());
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (InputStream ignored = process.getInputStream()) {
            while (ignored.read() != -1) {
            }
        }
        return process.waitFor();
    }

    private static boolean isArc(String owner) {
        return owner.startsWith("arc/") || owner.startsWith("io/anuke/arc/");
    }

    private static boolean isGameOrArc(String owner) {
        return isArc(owner) || owner.startsWith("mindustry/") || owner.startsWith("io/anuke/mindustry/");
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--"))
                options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("missing --" + key);
        return value.trim();
    }
}
