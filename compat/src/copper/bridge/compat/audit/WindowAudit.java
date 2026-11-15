package copper.bridge.compat.audit;

import copper.bridge.compat.refs.ClassRef;
import copper.bridge.compat.refs.ClassRefs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Reports the game and arc types a branch references whose source appears, disappears and appears again across the
 * release tags of its window. The endpoints alone cannot show that pattern, and a branch referencing such a type works
 * on the ends and fails in the middle.
 *
 * <p>It reads source trees, not jars: the check runs against the game and arc clones the release build already needs,
 * and downloads nothing. Build ids have no tag, so a bleeding-edge window cannot be audited this way; the jars that
 * are already staged remain the compile-side evidence there.
 *
 * <p>Usage: {@code --branch v2 --classes <dir> --mindustry <clone> --arc <clone> --from 84 --to 91}.
 */
public final class WindowAudit {

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        Path classes = Paths.get(required(options, "classes"));
        Path mindustry = Paths.get(required(options, "mindustry"));
        Path arc = Paths.get(required(options, "arc"));
        double from = Double.parseDouble(required(options, "from"));
        String toText = options.getOrDefault("to", "").trim();
        double to = toText.isEmpty() ? Double.POSITIVE_INFINITY : Double.parseDouble(toText);

        Set<String> owners = new TreeSet<>();
        for (ClassRef ref : ClassRefs.read(classes)) {
            if (isGameOrArc(ref.owner))
                owners.add(ref.owner);
        }

        Map<String, List<String>> tagCache = new HashMap<>();
        Map<String, Set<String>> pathCache = new HashMap<>();
        List<String> findings = new ArrayList<>();
        for (String owner : owners) {
            boolean arcOwner = isArc(owner);
            Path clone = arcOwner ? arc : mindustry;
            List<String> tags = tags(clone, from, to, tagCache);
            if (tags.size() < 2)
                continue;

            String base = owner.contains("$") ? owner.substring(0, owner.indexOf('$')) : owner;
            String suffix = "/" + base + ".java";
            StringBuilder sequence = new StringBuilder();
            int changes = 0;
            int last = -1;
            for (String tag : tags) {
                int present = source(clone, tag, suffix, pathCache) ? 1 : 0;
                sequence.append(present);
                if (last >= 0 && present != last)
                    changes++;
                last = present;
            }
            if (changes > 1)
                findings.add(owner + " " + sequence);
        }

        for (String finding : findings)
            System.out.println(branch + ": " + finding);
        System.out.println(branch + ": " + owners.size() + " referenced type(s), " + findings.size()
                + " transient type(s) across the release tags");
    }

    private static List<String> tags(Path clone, double from, double to, Map<String, List<String>> cache)
            throws IOException, InterruptedException {
        String key = clone + "|" + from + "|" + to;
        List<String> cached = cache.get(key);
        if (cached != null)
            return cached;

        List<String> tags = new ArrayList<>();
        for (String tag : output(clone, "tag", "--list", "v*")) {
            double value;
            try {
                value = Double.parseDouble(tag.substring(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (value >= from && value <= to)
                tags.add(tag);
        }
        tags.sort(Comparator.comparingDouble(tag -> Double.parseDouble(tag.substring(1))));
        cache.put(key, tags);
        return tags;
    }

    private static boolean source(Path clone, String tag, String suffix, Map<String, Set<String>> cache)
            throws IOException, InterruptedException {
        String key = clone + "|" + tag;
        Set<String> paths = cache.get(key);
        if (paths == null) {
            paths = new HashSet<>(output(clone, "ls-tree", "-r", "--name-only", tag));
            cache.put(key, paths);
        }
        for (String path : paths) {
            if (path.endsWith(suffix))
                return true;
        }
        return false;
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
