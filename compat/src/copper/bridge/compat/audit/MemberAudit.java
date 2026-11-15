package copper.bridge.compat.audit;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;

import copper.bridge.compat.refs.*;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reports game and arc methods, fields and nested classes that appear, disappear and appear again while the outer
 * source file stays present. A path-level audit cannot see a nested type or a member vanish inside the same file;
 * this one parses the Java source at every commit that touches the file.
 *
 * <p>Reads source with the javac parser; parse errors are reported, not skipped. File contents come from one
 * {@code git cat-file --batch} per repository.
 */
public final class MemberAudit {

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String branch = required(options, "branch");
        Path classes = Paths.get(required(options, "classes"));
        Path mindustry = Paths.get(required(options, "mindustry"));
        Path arc = Paths.get(required(options, "arc"));
        String from = required(options, "from");
        String to = options.getOrDefault("to", "").trim();
        if (to.isEmpty())
            to = "HEAD";

        List<MemberDep> deps = deps(classes);
        List<String> issues = new ArrayList<>();
        Map<String, String> sequences = new HashMap<>();
        int commits = 0;
        for (Path clone : new Path[]{mindustry, arc}) {
            List<MemberDep> repoDeps = deps.stream()
                    .filter(dep -> (isArc(dep.owner) ? arc : mindustry).equals(clone))
                    .collect(Collectors.toList());
            if (repoDeps.isEmpty())
                continue;
            WindowAudit.Resolved resolved = WindowAudit.resolve(clone, mindustry, from, to);
            if (resolved == null) {
                issues.add("no " + (clone.equals(arc) ? "arc" : "game") + " source for " + from + ".." + to);
                continue;
            }
            if (!resolved.from.equals(from) || !resolved.to.equals(to))
                System.out.println(branch + ": " + (clone.equals(arc) ? "arc" : "game") + " " + from + ".." + to
                        + " -> " + resolved.from.substring(0, Math.min(10, resolved.from.length())) + ".."
                        + resolved.to.substring(0, Math.min(10, resolved.to.length())) + " by game tag time");
            Map<String, String> result = history(clone, resolved.from, resolved.to, repoDeps, issues);
            sequences.putAll(result);
            commits = Math.max(commits, count(clone, resolved.from, resolved.to));
        }

        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, String> entry : sequences.entrySet()) {
            String sequence = entry.getValue();
            int changes = 0;
            for (int i = 1; i < sequence.length(); i++) {
                if (sequence.charAt(i) != sequence.charAt(i - 1))
                    changes++;
            }
            if (changes > 1)
                findings.add(entry.getKey() + " " + sequence);
        }
        for (String finding : findings)
            System.out.println(branch + ": member " + finding);
        for (String issue : issues)
            System.out.println(branch + ": " + issue);
        System.out.println(branch + ": " + deps.size() + " member target(s), " + findings.size()
                + " transient member(s) across " + commits + " commit(s)");
        if (!issues.isEmpty())
            System.exit(1);
    }

    private static Map<String, String> history(Path clone, String from, String to, List<MemberDep> deps,
                                               List<String> issues) throws Exception {
        Map<String, List<MemberDep>> byOuter = new LinkedHashMap<>();
        for (MemberDep dep : deps)
            byOuter.computeIfAbsent(dep.outer, key -> new ArrayList<>()).add(dep);

        Set<String> fromTree = new HashSet<>(output(clone, "ls-tree", "-r", "--name-only", from));
        Set<String> toTree = new HashSet<>(output(clone, "ls-tree", "-r", "--name-only", to));
        Map<String, String> sequences = new HashMap<>();
        Map<String, String> contents = new HashMap<>();
        for (Map.Entry<String, List<MemberDep>> entry : byOuter.entrySet()) {
            String path = sourcePath(fromTree, entry.getKey());
            if (path == null)
                path = sourcePath(toTree, entry.getKey());
            if (path == null)
                path = historicalPath(clone, entry.getKey());
            if (path == null) {
                issues.add("no source path for " + entry.getKey() + " in " + from + ".." + to);
                continue;
            }

            List<String> commits = new ArrayList<>();
            commits.add(from);
            commits.addAll(output(clone, "log", "--reverse", "--format=%H", from + ".." + to, "--", path));
            List<String> queries = new ArrayList<>();
            for (String commit : commits)
                queries.add(commit + ":" + path);
            contents = contents(clone, queries);
            Set<String> keys = new HashSet<>();
            List<String> sequence = new ArrayList<>();
            for (int i = 0; i < commits.size(); i++) {
                String content = contents.get(queries.get(i));
                keys.clear();
                if (content != null)
                    keys.addAll(Source.parse(content, path, issues));
                for (MemberDep dep : entry.getValue()) {
                    boolean present = false;
                    for (String key : keys) {
                        if (dep.matches(key)) {
                            present = true;
                            break;
                        }
                    }
                    sequence.add(present ? "1" : "0");
                }
            }
            for (int i = 0; i < entry.getValue().size(); i++) {
                StringBuilder value = new StringBuilder();
                for (int c = 0; c < commits.size(); c++)
                    value.append(sequence.get(c * entry.getValue().size() + i));
                sequences.put(entry.getValue().get(i).toString(), value.toString());
            }
        }
        return sequences;
    }

    private static String sourcePath(Set<String> tree, String outer) {
        String suffix = "/" + outer + ".java";
        for (String path : tree) {
            if (path.endsWith(suffix))
                return path;
        }
        return null;
    }

    /** Finds a file that exists only inside the window, such as a type that appears and disappears there. */
    private static String historicalPath(Path clone, String outer) throws Exception {
        String base = outer.substring(outer.lastIndexOf('/') + 1);
        String suffix = "/" + outer + ".java";
        for (String path : output(clone, "log", "--all", "--format=", "--name-only", "--",
                ":(glob)**/" + base + ".java")) {
            if (path.endsWith(suffix))
                return path;
        }
        return null;
    }

    private static Map<String, String> contents(Path clone, List<String> queries) throws Exception {
        Map<String, String> contents = new HashMap<>();
        Process process = new ProcessBuilder("git", "-C", clone.toString(), "cat-file", "--batch")
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try (OutputStream out = process.getOutputStream(); InputStream in = process.getInputStream()) {
            for (String query : queries) {
                out.write((query + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                String header = readLine(in);
                if (header == null)
                    break;
                if (header.endsWith(" missing")) {
                    contents.put(query, null);
                    continue;
                }
                String[] parts = header.split(" ");
                int size = Integer.parseInt(parts[2]);
                byte[] content = in.readNBytes(size);
                contents.put(query, new String(content, StandardCharsets.UTF_8));
                in.read();
            }
        }
        if (process.waitFor() != 0)
            throw new IOException("git cat-file failed in " + clone);
        return contents;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1) {
            if (value == '\n')
                return line.toString(StandardCharsets.UTF_8);
            line.write(value);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
    }

    private static int count(Path clone, String from, String to) throws Exception {
        return Integer.parseInt(output(clone, "rev-list", "--count", from + ".." + to).get(0).trim());
    }

    private static List<MemberDep> deps(Path classes) {
        Map<String, MemberDep> deps = new LinkedHashMap<>();
        for (ClassRef ref : ClassRefs.read(classes)) {
            if (!game(ref.owner))
                continue;
            MemberDep dep = ref.descriptor.startsWith("(") ? MemberDep.method(ref.owner, ref.name, ref.descriptor)
                    : MemberDep.field(ref.owner, ref.name);
            deps.putIfAbsent(dep.key(), dep);
            if (ref.owner.contains("$"))
                addNested(deps, ref.owner);
        }
        for (BridgeOverrideRef ref : BridgeOverrideRefs.read(classes)) {
            if (!game(ref.owner))
                continue;
            MemberDep dep = MemberDep.method(ref.owner, ref.name, ref.descriptor);
            deps.putIfAbsent(dep.key(), dep);
            if (ref.owner.contains("$"))
                addNested(deps, ref.owner);
        }
        ReflectionRefs.Result reflection = ReflectionRefs.read(classes);
        for (ReflectionRef ref : reflection.refs) {
            if (!game(ref.owner))
                continue;
            MemberDep dep;
            if (ref.kind == ReflectionRef.Kind.TYPE) {
                if (!ref.owner.contains("$"))
                    continue;
                dep = MemberDep.type(ref.owner);
            } else if (ref.kind == ReflectionRef.Kind.FIELD) {
                dep = MemberDep.field(ref.owner, ref.member);
            } else {
                dep = new MemberDep(ref.owner, ref.kind == ReflectionRef.Kind.CONSTRUCTOR
                        ? MemberDep.Kind.CONSTRUCTOR : MemberDep.Kind.METHOD, ref.member, -1);
            }
            deps.putIfAbsent(dep.key(), dep);
            if (ref.owner.contains("$"))
                addNested(deps, ref.owner);
        }
        for (TypeDep dep : TypeDeps.read(classes)) {
            if (game(dep.owner) && dep.owner.contains("$"))
                addNested(deps, dep.owner);
        }
        return new ArrayList<>(deps.values());
    }

    private static void addNested(Map<String, MemberDep> deps, String owner) {
        String current = owner;
        while (current.contains("$")) {
            MemberDep dep = MemberDep.type(current);
            deps.putIfAbsent(dep.key(), dep);
            current = current.substring(0, current.indexOf('$'));
        }
    }

    private static boolean game(String owner) {
        return owner.startsWith("arc/") || owner.startsWith("io/anuke/arc/")
                || owner.startsWith("mindustry/") || owner.startsWith("io/anuke/mindustry/");
    }

    private static boolean isArc(String owner) {
        return owner.startsWith("arc/") || owner.startsWith("io/anuke/arc/");
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

    /** Extracts the class, method and field declarations from one Java source file. */
    private static final class Source {
        private static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

        static Set<String> parse(String content, String path, List<String> issues) {
            Set<String> keys = new HashSet<>();
            if (COMPILER == null) {
                issues.add("no javac parser for " + path);
                return keys;
            }
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + Math.abs(path.hashCode())
                    + "/Source.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return content;
                }
            };
            try {
                JavacTask task = (JavacTask)COMPILER.getTask(null, null, diagnostics,
                        List.of("-proc:none"), null, List.of(file));
                for (CompilationUnitTree unit : task.parse()) {
                    String pkg = unit.getPackageName() == null ? ""
                            : unit.getPackageName().toString().replace('.', '/') + "/";
                    for (Tree declaration : unit.getTypeDecls())
                        collect(declaration, pkg, keys);
                }
            } catch (Throwable e) {
                issues.add("source parse failed in " + path + ": " + e);
            }
            for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    issues.add("source parse error in " + path + ": " + diagnostic.getMessage(null));
            }
            return keys;
        }

        private static void collect(Tree tree, String owner, Set<String> keys) {
            if (!(tree instanceof ClassTree))
                return;
            ClassTree type = (ClassTree)tree;
            String simple = type.getSimpleName().toString();
            if (simple.isEmpty())
                return;
            String name = owner + simple;
            keys.add("C:" + name);
            for (Tree member : type.getMembers()) {
                if (member instanceof ClassTree) {
                    collect(member, name + "$", keys);
                } else if (member instanceof MethodTree) {
                    MethodTree method = (MethodTree)member;
                    boolean constructor = method.getReturnType() == null;
                    keys.add("M:" + name + "#" + (constructor ? "<init>" : method.getName())
                            + "/" + method.getParameters().size());
                } else if (member instanceof VariableTree) {
                    keys.add("F:" + name + "." + ((VariableTree)member).getName());
                }
            }
        }
    }
}
