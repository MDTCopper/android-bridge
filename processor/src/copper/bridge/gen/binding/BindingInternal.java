package copper.bridge.gen.binding;

/**
 * Writes {@code gen/internal/binding.h}: the vocabulary binding.cpp is written in. None of it leaves the
 * module, which is why it is not in the public header. The shapes are fixed and nothing here reads the
 * annotations;
 */
final class BindingInternal {
    private BindingInternal() {
    }

    static String of() {
        return """
                // Generated. Do not edit.
                #ifndef COPPER_BRIDGE_GEN_INTERNAL_BINDING_H
                #define COPPER_BRIDGE_GEN_INTERNAL_BINDING_H

                #include "jni/side.h"

                #include <jni.h>

                namespace copper::bridge::gen::Binding {

                    // The binding layer's vocabulary: what a registration table is made of, and what a reverse handle
                    // is. None of it leaves this module, so it is here rather than in the public header; the shapes are
                    // fixed, while the rows binding.cpp holds are not, and this is the shape they are written in.

                    /** One native method of one class: the name and descriptor the JVM declared, and its function. */
                    struct NativeEntry {
                        const char* name;
                        const char* descriptor;
                        void* fn;
                    };

                    struct Table {
                        const char* className;
                        const NativeEntry* entries;
                        int count;
                    };

                    /** One handle a reverse call goes through: a class and a method, resolved once and never released.
                     *
                     *  <p>The handles are storage that exists with this library and nothing more: what they hold is
                     *  filled in by Binding::ResolveReverse, which JNI_OnLoad calls on the thread whose loader can see
                     *  the classes. A handle that was never resolved stays empty, and every call checks that first.</p> */
                    struct Reverse {
                        /** One virtual machine's side of a member: its class and its method. */
                        struct Handle {
                            jclass clazz = nullptr;
                            jmethodID method = nullptr;

                            bool Ready() const { return clazz != nullptr && method != nullptr; }
                        };

                        // One per side, because a class belongs to the VM that loaded it: a member both VMs can see has
                        // two classes and two method ids, and the one resolved through ART's loader is not one the JVM
                        // can call.
                        Handle art;
                        Handle jvm;

                        Handle& Of(jni::Side side) {
                            return side == jni::Side::Art ? art : jvm;
                        }
                    };

                    /** One reverse member, as the walk that resolves it needs to see it. */
                    struct ReverseEntry {
                        const char* owner_class;
                        const char* method_name;
                        const char* descriptor;
                    };

                    // The handles, one slot per member that needs one, in the order REVERSE_ENTRIES lists them: the walk
                    // resolves slot i from row i, and the call through slot i is the one whose declaration produced that
                    // row. Arrays rather than one named handle per member, because a name per member would be one more
                    // generated name to collide with, and nothing reads a handle by name anyway.
                    extern Reverse REVERSE_HANDLES[];
                    extern const ReverseEntry REVERSE_ENTRIES[];

                } // namespace copper::bridge::gen::Binding

                #endif // COPPER_BRIDGE_GEN_INTERNAL_BINDING_H
                """;
    }
}
