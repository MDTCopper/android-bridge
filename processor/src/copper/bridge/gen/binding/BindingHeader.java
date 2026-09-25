package copper.bridge.gen.binding;

/**
 * Writes {@code gen/binding.h}: what the rest of the library asks the binding layer for.
 *
 * <p>The declarations are fixed, so nothing here reads the annotations: the tables that implement them are
 * written from the declarations into binding.cpp. It is generated rather than committed because the
 * directory it lives in is generated, and a second copy of a declaration is a second source of truth.</p>
 */
final class BindingHeader {
    private BindingHeader() {
    }

    static String of() {
        return """
                // Generated. Do not edit.
                #ifndef COPPER_BRIDGE_GEN_BINDING_H
                #define COPPER_BRIDGE_GEN_BINDING_H

                #include "jni/side.h"

                #include <jni.h>

                namespace copper::bridge::gen::Binding {

                    // What the rest of the library needs from the binding layer: how a reverse call went, and the two
                    // walks it asks for.
                    //
                    // What a table, a handle and an outcome are is not here: none of them leaves this module, so they
                    // live in internal/binding.h, and the calls themselves - the other direction - live in vmcall.h.

                    /** How one of the reverse calls went. The two failures are told apart because the callers report them
                     *  differently: a handle that was never resolved is a wiring fact, a failed call is a VM that did not
                     *  answer. */
                    enum class Outcome { Done, NotResolved, Failed };

                    /** Registers the tables whose class this VM can see. Returns how many resolved. */
                    int BindAll(JNIEnv* env);

                    /** Resolves the reverse handles for one side, and does nothing on a second call for that side.
                     *
                     *  <p>Per side, because a handle is a class of one VM: each load resolves the handles of the VM it
                     *  belongs to, which is why the caller says which side it is. Idempotent, so a member already resolved
                     *  is left alone.</p> */
                    void ResolveReverse(JNIEnv* env, jni::Side side);

                } // namespace copper::bridge::gen::Binding

                #endif // COPPER_BRIDGE_GEN_BINDING_H
                """;
    }
}
