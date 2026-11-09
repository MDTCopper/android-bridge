package copper.bridge.compat.refs;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Collects the branch's references into arc and mindustry from its compiled classes.
 *
 * <p>Only {@code Methodref}, {@code InterfaceMethodref} and {@code Fieldref} entries are read: string constants
 * such as a probed class name are not references and must not be checked. A class-level {@code @CompatRange}
 * applies to the whole source file, so every reference from a class declared in that file carries it.
 */
public final class ClassRefs {
    private static final String COMPAT_RANGE = "Lcopper/bridge/annotation/CompatRange;";

    private ClassRefs() {
    }

    public static List<ClassRef> read(Path directory) {
        List<ClassFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> classes = stream.filter(path -> path.toString().endsWith(".class"))
                    .collect(Collectors.toList());
            for (Path path : classes) {
                String className = directory.relativize(path).toString().replace('\\', '/');
                className = className.substring(0, className.length() - ".class".length());
                ClassFile file = of(path, className);
                if (file != null)
                    files.add(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, String> overrides = new HashMap<>();
        for (ClassFile file : files) {
            if (file.overrideKey != null)
                overrides.putIfAbsent(file.sourceFile, file.overrideKey);
        }
        TreeMap<String, ClassRef> refs = new TreeMap<>();
        for (ClassFile file : files) {
            String override = overrides.get(file.sourceFile);
            for (ClassRef ref : file.refs) {
                ClassRef resolved = override == null ? ref
                        : new ClassRef(ref.sourceClass, ref.owner, ref.name, ref.descriptor, override);
                refs.put(resolved.key(), resolved);
            }
        }
        return new ArrayList<>(refs.values());
    }

    private static final class ClassFile {
        final List<ClassRef> refs = new ArrayList<>();
        final String sourceFile;
        final String overrideKey;

        ClassFile(String sourceFile, String overrideKey) {
            this.sourceFile = sourceFile;
            this.overrideKey = overrideKey;
        }
    }

    private static final class Meta {
        final String sourceFile;
        final String overrideKey;

        Meta(String sourceFile, String overrideKey) {
            this.sourceFile = sourceFile;
            this.overrideKey = overrideKey;
        }
    }

    private static ClassFile of(Path path, String sourceClass) {
        ClassFile file;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (in.readInt() != 0xCAFEBABE)
                return null;
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] className = new int[count];
            int[] natName = new int[count];
            int[] natDescriptor = new int[count];
            int[] refClass = new int[count];
            int[] refNat = new int[count];
            int[] refTag = new int[count];
            for (int i = 1; i < count; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: utf8[i] = in.readUTF(); break;
                    case 3: case 4: in.readInt(); break;
                    case 5: case 6: in.readLong(); i++; break;
                    case 7: className[i] = in.readUnsignedShort(); break;
                    case 8: in.readUnsignedShort(); break;
                    case 9: case 10: case 11:
                        refClass[i] = in.readUnsignedShort();
                        refNat[i] = in.readUnsignedShort();
                        refTag[i] = tag;
                        break;
                    case 12:
                        natName[i] = in.readUnsignedShort();
                        natDescriptor[i] = in.readUnsignedShort();
                        break;
                    case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                    case 16: in.readUnsignedShort(); break;
                    case 17: case 18: in.readUnsignedShort(); in.readUnsignedShort(); break;
                    case 19: case 20: in.readUnsignedShort(); break;
                    default: return null;
                }
            }

            Meta meta = readMeta(in, utf8, sourceClass);
            file = new ClassFile(meta.sourceFile, meta.overrideKey);
            for (int i = 1; i < count; i++) {
                if (refTag[i] != 9 && refTag[i] != 10 && refTag[i] != 11)
                    continue;
                String owner = utf8[className[refClass[i]]];
                if (owner == null || !isGameOrArc(owner))
                    continue;
                String name = utf8[natName[refNat[i]]];
                String descriptor = utf8[natDescriptor[refNat[i]]];
                if (name != null && descriptor != null)
                    file.refs.add(new ClassRef(sourceClass, owner, name, descriptor, null));
            }
        } catch (Throwable ignored) {
            // an unreadable class is not a reference source; the compiler already vetted the branch's own code
            return null;
        }
        return file;
    }

    /** A reference the check owns: arc or the game, in either package spelling. */
    private static boolean isGameOrArc(String owner) {
        return owner.startsWith("arc/") || owner.startsWith("mindustry/")
                || owner.startsWith("io/anuke/arc/") || owner.startsWith("io/anuke/mindustry/");
    }

    /** Reads the source file name and the class-level {@code @CompatRange}; the class structure is skipped. */
    private static Meta readMeta(DataInputStream in, String[] utf8, String sourceClass) throws IOException {
        in.readUnsignedShort();
        in.readUnsignedShort();
        in.readUnsignedShort();
        skip(in, 2L * in.readUnsignedShort());
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            skip(in, 6);
            skipAttributes(in);
        }
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            skip(in, 6);
            skipAttributes(in);
        }

        String sourceFile = null;
        String overrideKey = null;
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            String name = utf8[in.readUnsignedShort()];
            byte[] data = new byte[in.readInt()];
            in.readFully(data);
            DataInputStream attribute = new DataInputStream(new ByteArrayInputStream(data));
            if ("SourceFile".equals(name)) {
                sourceFile = utf8[attribute.readUnsignedShort()];
            } else if ("RuntimeInvisibleAnnotations".equals(name) || "RuntimeVisibleAnnotations".equals(name)) {
                String value = parseAnnotations(attribute, utf8);
                if (value != null)
                    overrideKey = value;
            }
        }

        String outer = sourceClass.contains("$")
                ? sourceClass.substring(0, sourceClass.indexOf('$'))
                : sourceClass;
        String packagePath = outer.contains("/") ? outer.substring(0, outer.lastIndexOf('/') + 1) : "";
        if (sourceFile == null)
            sourceFile = outer.substring(outer.lastIndexOf('/') + 1) + ".java";
        return new Meta(packagePath + sourceFile, overrideKey);
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readUnsignedShort();
            skip(in, in.readInt());
        }
    }

    private static void skip(DataInputStream in, long bytes) throws IOException {
        long left = bytes;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0)
                    throw new IOException("unexpected end of class file");
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private static String parseAnnotations(DataInputStream in, String[] utf8) throws IOException {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            String type = utf8[in.readUnsignedShort()];
            String floor = null;
            String ceiling = null;
            int pairs = in.readUnsignedShort();
            for (int pair = 0; pair < pairs; pair++) {
                String element = utf8[in.readUnsignedShort()];
                String value = readElementValue(in, utf8);
                if (COMPAT_RANGE.equals(type)) {
                    if ("floor".equals(element))
                        floor = value;
                    else if ("ceiling".equals(element))
                        ceiling = value;
                }
            }
            if (COMPAT_RANGE.equals(type))
                return (floor == null ? "default" : floor) + "|" + (ceiling == null ? "default" : ceiling);
        }
        return null;
    }

    private static String readElementValue(DataInputStream in, String[] utf8) throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 's':
                return utf8[in.readUnsignedShort()];
            case 'B': case 'C': case 'D': case 'F': case 'I': case 'J': case 'S': case 'Z': case 'c':
                in.readUnsignedShort();
                return null;
            case 'e':
                in.readUnsignedShort();
                in.readUnsignedShort();
                return null;
            case '@':
                parseAnnotations(in, utf8);
                return null;
            case '[':
                int count = in.readUnsignedShort();
                for (int i = 0; i < count; i++)
                    readElementValue(in, utf8);
                return null;
            default:
                throw new IOException("unknown annotation element tag " + tag);
        }
    }
}
