package copper.bridge.compat;

import copper.bridge.compat.check.*;
import copper.bridge.compat.config.CheckConfig;
import copper.bridge.compat.refs.*;
import copper.bridge.compat.sources.RefJars;

import java.nio.file.*;
import java.util.*;

/**
 * Build-time API check: the branch compiles against one epoch but runs on every version of its range, so a
 * member whose declaration changed inside the range still compiles and only fails on the older device.
 *
 * <p>Reads the compiled branch classes, keeps the references into arc and mindustry, and compares their
 * declarations at the two ends of the branch's range. Both ends are official desktop jars that the build
 * stages and passes in as {@code --jar <ref>=<path>}; the check itself never downloads. Additions are
 * allowed; a changed or removed declaration fails the build. A class annotated with {@code @CompatRange} gets
 * that ref as its file's ends.
 */
public final class Check {

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        CheckConfig config = CheckConfig.load(Paths.get(required(options, "properties")), branch);
        Path bridge = Paths.get(required(options, "bridge"));

        List<ClassRef> refs = ClassRefs.read(bridge);
        Range branchRange = new Range(config.floor, config.ceiling);
        Map<String, Range> overrides = new HashMap<>();
        for (ClassRef ref : refs) {
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

            List<String> issues = ApiComparer.compare(refs, branchRange, overrides, jars);
            System.out.println(branch + ": checking " + refs.size() + " referenced arc/mindustry member(s)");
            System.out.println("  floor   " + config.floor);
            System.out.println("  ceiling " + config.ceiling);
            for (String issue : issues)
                System.out.println("  " + issue);
            if (issues.isEmpty()) {
                System.out.println("API check passed");
            } else {
                System.out.println("API check FAILED: " + issues.size() + " incompatible reference(s)");
                System.exit(1);
            }
        }
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
}
