package copper.bridge.compat.audit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Verifies that a branch's release tag interval lies inside its BE build interval. The audits then use the BE
 * endpoints, so a release-only endpoint cannot hide a change that happened between BE builds.
 */
public final class WindowContainment {
    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        Path game = Paths.get(required(options, "mindustry"));
        Path properties = Paths.get(required(options, "properties"));
        String releaseFrom = options.get("release-from");
        String releaseTo = options.get("release-to");
        String beFrom = required(options, "be-from");
        String beTo = required(options, "be-to");

        Properties table = new Properties();
        try (InputStream in = Files.newInputStream(properties)) {
            table.load(in);
        }
        long beStart = timestamp(game, commit(table, beFrom));
        List<String> issues = new ArrayList<>();
        if (releaseFrom != null && !releaseFrom.isEmpty()
                && timestamp(game, ref(game, releaseFrom)) < beStart)
            issues.add("release " + releaseFrom + " starts before BE " + beFrom);
        long beEnd = timestamp(game, commit(table, beTo));
        if (releaseTo != null && !releaseTo.isEmpty()
                && timestamp(game, ref(game, releaseTo)) > beEnd)
            issues.add("release " + releaseTo + " ends after BE " + beTo);

        if (issues.isEmpty())
            System.out.println(branch + ": release interval is inside BE " + beFrom + ".." + beTo);
        for (String issue : issues)
            System.out.println(branch + ": " + issue);
        if (!issues.isEmpty())
            System.exit(1);
    }

    private static String commit(Properties table, String ref) {
        String commit = table.getProperty("audit.be." + ref);
        if (commit == null || commit.trim().isEmpty())
            throw new IllegalArgumentException("no audit.be." + ref + " in gradle.properties");
        return commit.trim();
    }

    private static String ref(Path game, String ref) throws Exception {
        List<String> lines = output(game, "rev-parse", "--verify", "--quiet", ref + "^{commit}");
        if (lines.isEmpty())
            throw new IllegalArgumentException("no git commit for release tag " + ref);
        return lines.get(0).trim();
    }

    private static long timestamp(Path game, String ref) throws Exception {
        return Long.parseLong(output(game, "show", "-s", "--format=%ct", ref).get(0).trim());
    }

    private static List<String> output(Path game, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(game.toString());
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
            throw new IOException("git " + String.join(" ", args) + " failed in " + game);
        return lines;
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
