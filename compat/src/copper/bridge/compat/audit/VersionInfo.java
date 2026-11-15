package copper.bridge.compat.audit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/** Maps a bleeding-edge build id to a commit through version.properties or the audited build table. */
final class VersionInfo {
    private VersionInfo() {
    }

    static String commit(Path jars, String ref, Path game, Path properties, List<String> issues) throws IOException {
        if (properties != null) {
            Properties table = new Properties();
            try (InputStream in = Files.newInputStream(properties)) {
                table.load(in);
            }
            String hardcoded = table.getProperty("audit.be." + ref);
            if (hardcoded != null) {
                String commit = revParse(game, hardcoded.trim());
                if (commit != null)
                    return commit;
                issues.add("audit.be." + ref + " commit " + hardcoded + " is not in " + game);
                return null;
            }
        }

        Path directory = jars.resolve(ref);
        if (!Files.isDirectory(directory)) {
            issues.add("no staged jar for " + ref + " under " + jars);
            return null;
        }
        Path jar = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path candidate : stream) {
                if (!candidate.getFileName().toString().endsWith("-sources.jar")) {
                    jar = candidate;
                    break;
                }
            }
        } catch (IOException e) {
            issues.add("cannot list jars for " + ref + ": " + e);
            return null;
        }
        if (jar == null) {
            issues.add("no jar for " + ref + " under " + directory);
            return null;
        }

        Properties version = new Properties();
        String comment = null;
        try (JarFile archive = new JarFile(jar.toFile())) {
            ZipEntry entry = archive.getEntry("version.properties");
            if (entry == null) {
                issues.add("no version.properties in " + jar);
                return null;
            }
            try (InputStream in = archive.getInputStream(entry)) {
                version.load(in);
            }
            try (InputStream in = archive.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") && line.contains("UTC")) {
                        comment = line.substring(1).trim();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            issues.add("cannot read " + jar + ": " + e);
            return null;
        }

        String hash = version.getProperty("commitHash", "").trim();
        if (!hash.isEmpty() && !"unknown".equals(hash)) {
            String commit = revParse(game, hash);
            if (commit != null)
                return commit;
            issues.add("commitHash " + hash + " from " + ref + " is not in " + game);
            return null;
        }

        long epoch = epoch(comment);
        if (epoch < 0) {
            issues.add("no commitHash or build date in " + jar);
            return null;
        }
        String commit = before(game, epoch);
        if (commit == null)
            issues.add("no game commit before " + ref + " build date");
        return commit;
    }

    private static long epoch(String comment) {
        if (comment == null)
            return -1;
        try {
            return new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
                    .parse(comment).getTime() / 1000;
        } catch (ParseException e) {
            return -1;
        }
    }

    private static String revParse(Path clone, String ref) {
        try {
            List<String> lines = output(clone, "rev-parse", "--verify", "--quiet", ref + "^{commit}");
            return lines.isEmpty() ? null : lines.get(0).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String before(Path clone, long epoch) {
        try {
            List<String> lines = output(clone, "rev-list", "-1", "--before=" + (epoch - 1), "HEAD");
            return lines.isEmpty() ? null : lines.get(0).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> output(Path clone, String... args) throws Exception {
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
}
