package copper.bridge.compat.sources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs git against one clone; the check reads sources only, never a build output. */
public final class Git {
    private final String clone;

    public Git(String clone) {
        this.clone = clone;
    }

    public String run(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(clone);
        command.addAll(Arrays.asList(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1)
                    out.write(buffer, 0, read);
            }
            int code = process.waitFor();
            if (code != 0)
                throw new IllegalStateException("git failed (" + code + "): " + String.join(" ", command));
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("git failed: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git interrupted: " + String.join(" ", command), e);
        }
    }

    public String show(String commit, String path) {
        return run("show", commit + ":" + path);
    }
}
