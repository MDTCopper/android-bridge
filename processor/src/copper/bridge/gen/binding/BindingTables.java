package copper.bridge.gen.binding;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes {@code gen/binding.cpp}: the table of classes that declare natives, the slots the reverse calls
 * are resolved into, and the walk that fills both.
 *
 * <p>The entries the tables point at are declared here rather than included, so a signature that
 * disagrees with one of them is a link error and a table cannot point at a function that is not there.</p>
 */
final class BindingTables {
    private BindingTables() {
    }

    static String of(BindingProcessor processor) {
        List<Template> forwards = new ArrayList<>();
        for (Map.Entry<String, List<Forward>> group : byNamespace(processor).entrySet()) {
            List<Template> rows = new ArrayList<>();
            for (Forward row : group.getValue())
                rows.add(Template.of("""
                        {{result}} {{entry}}(JNIEnv*, {{receiver}}{{params}});
                        """)
                        .with("result", row.result.jni)
                        .with("entry", row.entryName)
                        .with("receiver", row.receiver)
                        .with("params", row.parameters()));
            forwards.add(Template.of("""
                    namespace {{name}} {
                        {{rows}}
                    } // namespace {{name}}
                    """)
                    .with("name", group.getKey())
                    .with("rows", Template.join(rows, "\n")));
        }

        List<Template> natives = new ArrayList<>();
        for (Map.Entry<String, List<Forward>> group : processor.forwards().entrySet()) {
            List<Template> rows = new ArrayList<>();
            for (Forward row : group.getValue())
                rows.add(Template.of("""
                        { "{{method}}", "{{descriptor}}", reinterpret_cast<void*>(&{{namespace}}::{{entry}}) },
                        """)
                        .with("method", row.method)
                        .with("descriptor", row.descriptor)
                        .with("namespace", row.entryNamespace)
                        .with("entry", row.entryName));
            natives.add(Template.of("""
                    const NativeEntry {{table}}[] = {
                        {{rows}}
                    };
                    """)
                    .with("table", group.getValue().get(0).table())
                    .with("rows", Template.join(rows, "\n")));
        }

        List<Template> tables = new ArrayList<>();
        for (Map.Entry<String, List<Forward>> group : processor.forwards().entrySet()) {
            String table = group.getValue().get(0).table();
            tables.add(Template.of("""
                    { "{{owner}}", {{table}}, static_cast<int>(sizeof({{table}}) / sizeof(NativeEntry)) },
                    """)
                    .with("owner", group.getKey())
                    .with("table", table));
        }

        List<Template> handles = new ArrayList<>();
        for (Reverse member : processor.slotted())
            handles.add(Template.of("""
                    {}, // {{owner}}.{{method}}
                    """).with("owner", member.owner).with("method", member.method));

        List<Template> entries = new ArrayList<>();
        for (Reverse member : processor.slotted())
            entries.add(Template.of("""
                    { "{{path}}", "{{method}}", "{{descriptor}}" },
                    """)
                    .with("path", member.path)
                    .with("method", member.method)
                    .with("descriptor", member.descriptor));

        return Template.of("""
                // Generated. Do not edit.
                #include "gen/binding.h"

                #include "gen/internal/binding.h"
                #include "util/log.h"

                #include <cstddef>
                #include <string>

                // The entries the tables below point at, declared from the same declarations the tables are built
                // from. None of their headers is included: a definition whose signature disagrees with one of
                // these is a link error, which is what keeps a table from pointing at a function that is not there.

                {{forwards}}

                namespace copper::bridge::gen::Binding {

                    namespace {

                        std::string Slashed(const char* name) {
                            std::string path(name);
                            for (char& character : path) {
                                if (character == '.')
                                    character = '/';
                            }
                            return path;
                        }

                        // One table per class that declares natives, and the list the walk goes through. Both are
                        // file private: the walk is their only reader.

                        {{natives}}

                        const Table TABLES[] = {
                            {{tables}}
                        };

                        constexpr int TABLE_COUNT = static_cast<int>(sizeof(TABLES) / sizeof(Table));

                        /** Resolves one handle, if it is not resolved yet and this VM can see the class.
                         *
                         *  <p>A class this VM cannot see is not an error: the bridge's classes are on both
                         *  classpaths, but a class the other VM owns is not visible to this VM's loader, and the
                         *  load that can see it is the one that resolves it.</p> */
                        void ResolveInto(Reverse::Handle& slot, JNIEnv* env, const char* className,
                                const char* methodName, const char* descriptor) {
                            if (slot.Ready() || env == nullptr)
                                return;

                            jclass found = env->FindClass(className);
                            if (found == nullptr) {
                                env->ExceptionClear();
                                return;
                            }

                            jmethodID id = env->GetStaticMethodID(found, methodName, descriptor);
                            if (id == nullptr) {
                                env->ExceptionClear();
                                // The class is visible but the member is not in it, which the generated lookup cannot
                                // produce: an error reaches Android's log either way.
                                util::Log::ErrorF("JNI", "cannot resolve %s.%s%s", className, methodName, descriptor);
                                env->DeleteLocalRef(found);
                                return;
                            }

                            slot.clazz = static_cast<jclass>(env->NewGlobalRef(found));
                            slot.method = id;
                            env->DeleteLocalRef(found);
                        }

                    } // namespace

                    // One slot per member that needs a handle, and the row the walk resolves it from. The two are in
                    // the same order: slot i belongs to row i, and the call that reads slot i is the one whose
                    // declaration produced that row. A member of both VMs has no slot - each VM has its own class,
                    // and its call looks its own up when it is made.
                    Reverse REVERSE_HANDLES[] = {
                        {{handles}}
                    };

                    const ReverseEntry REVERSE_ENTRIES[] = {
                        {{entries}}
                    };

                    int BindAll(JNIEnv* env) {
                        int bound = 0;
                        for (int i = 0; i < TABLE_COUNT; i++) {
                            const Table& table = TABLES[i];
                            const std::string path = Slashed(table.className);

                            jclass clazz = env->FindClass(path.c_str());
                            if (clazz == nullptr) {
                                // Expected for a class of the other VM: this VM's class loaders cannot see it, and it
                                // is registered when that VM loads this library.
                                env->ExceptionClear();
                                continue;
                            }

                            bool ok = true;
                            for (int entry = 0; entry < table.count; entry++) {
                                const NativeEntry& native = table.entries[entry];
                                JNINativeMethod method{const_cast<char*>(native.name),
                                                       const_cast<char*>(native.descriptor), native.fn};
                                if (env->RegisterNatives(clazz, &method, 1) != JNI_OK) {
                                    env->ExceptionClear();
                                    // A table cannot disagree with the class it was written from, so reaching this means
                                    // the environment is broken - and an error reaches Android's log either way.
                                    util::Log::ErrorF("JNI", "RegisterNatives failed for %s.%s", table.className, native.name);
                                    ok = false;
                                }
                            }

                            env->DeleteLocalRef(clazz);
                            if (ok)
                                bound++;
                        }
                        return bound;
                    }

                    void ResolveReverse(JNIEnv* env, jni::Side side) {
                        for (size_t i = 0; i < sizeof(REVERSE_ENTRIES) / sizeof(ReverseEntry); i++) {
                            const ReverseEntry& entry = REVERSE_ENTRIES[i];
                            ResolveInto(REVERSE_HANDLES[i].Of(side), env, entry.owner_class, entry.method_name,
                                    entry.descriptor);
                        }
                    }

                } // namespace copper::bridge::gen::Binding
                """)
                .with("forwards", Template.join(forwards, "\n\n"))
                .with("natives", Template.join(natives, "\n\n"))
                .with("tables", Template.join(tables, "\n"))
                .with("handles", Template.join(handles, "\n"))
                .with("entries", Template.join(entries, "\n"))
                .render();
    }

    /** Each entry declared once, grouped by the namespace it lives in. */
    private static Map<String, List<Forward>> byNamespace(BindingProcessor processor) {
        Map<String, List<Forward>> grouped = new java.util.TreeMap<>();
        Set<String> declared = new LinkedHashSet<>();
        for (List<Forward> rows : processor.forwards().values()) {
            for (Forward row : rows) {
                // One entry may serve two classes (the batch pair does); repeating it says nothing.
                if (!declared.add(row.entryNamespace + "::" + row.entryName + row.signature()))
                    continue;
                grouped.computeIfAbsent(row.entryNamespace, key -> new ArrayList<>()).add(row);
            }
        }
        return grouped;
    }
}
