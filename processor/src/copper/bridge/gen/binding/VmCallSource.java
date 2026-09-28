package copper.bridge.gen.binding;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code gen/vmcall.cpp}: one function per member native reaches for. A pending exception is answered here,
 * because it would otherwise surface much later at an unrelated call.
 */
final class VmCallSource {
    private VmCallSource() {
    }

    static String of(BindingProcessor processor) {
        List<Reverse> slotted = processor.slotted();
        List<Template> calls = new ArrayList<>();
        for (Reverse member : processor.reverses())
            calls.add(call(member, slotted.indexOf(member)));

        return Template.of("""
                // Generated. Do not edit.
                #include "gen/vmcall.h"

                #include "gen/internal/binding.h"
                #include "jni/env.h"
                #include "jni/state.h"

                namespace copper::bridge::gen::VmCall {

                    {{calls}}

                } // namespace copper::bridge::gen::VmCall
                """)
                .with("calls", Template.join(calls, "\n\n"))
                .render();
    }

    private static Template call(Reverse member, int slot) {
        List<Template> parts = new ArrayList<>();

        // This member's handle for the side the call is made on: a member of both VMs has one per side.
        if (member.bothSides())
            parts.add(Template.of("""
                    if (env == nullptr)
                        return Binding::Outcome::Failed;

                    // The VM the caller is on is what picks the handle: it is the VM that owns the member,
                    // and that VM resolved its own side of it.
                    JavaVM* vm = nullptr;
                    env->GetJavaVM(&vm);
                    Binding::Reverse::Handle& slot = Binding::REVERSE_HANDLES[{{slot}}].Of(vm == jni::State::ArtVm() ? jni::Side::Art : jni::Side::Jvm);
                    if (!slot.Ready())
                        return Binding::Outcome::NotResolved;
                    """).with("slot", slot));
        else
            parts.add(Template.of("""
                    Binding::Reverse::Handle& slot = Binding::REVERSE_HANDLES[{{slot}}].Of(jni::Side::{{side}});
                    if (!slot.Ready())
                        return Binding::Outcome::NotResolved;
                    """).with("slot", slot).with("side", member.cppSide()));
        if (!member.bothSides())
            parts.add(Template.of("""
                    // The caller is on the other VM, so the environment has to be found - and a thread that is
                    // not coming back takes its attachment with it when this object dies.
                    jni::Env environment(jni::Side::{{side}}, {{detaches}});
                    if (!environment.Ok())
                        return Binding::Outcome::Failed;
                    JNIEnv* env = environment.Get();
                    """)
                    .with("side", member.cppSide())
                    .with("detaches", member.detaches));
        parts.add(Template.of("""
                {{call}}
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    return Binding::Outcome::Failed;
                }
                {{out}}
                return Binding::Outcome::Done;
                """)
                .with("call", jniCall(member))
                .with("out", member.result.isVoid ? "" : "if (out != nullptr)\n    *out = answer;"));

        return Template.of("""
                Binding::Outcome {{entry}}({{parameters}}) {
                    {{body}}
                }
                """)
                .with("entry", member.entry)
                .with("parameters", String.join(", ", member.parameters()))
                .with("body", Template.join(parts, "\n\n"));
    }

    private static String jniCall(Reverse member) {
        StringBuilder text = new StringBuilder("env->").append(member.callMethod())
                .append("(slot.clazz, slot.method");
        for (int i = 0; i < member.params.size(); i++)
            text.append(", arg").append(i);
        text.append(')');
        if (member.result.isVoid)
            return text.append(';').toString();
        if (member.result.object)
            return member.result.jni + " answer = static_cast<" + member.result.jni + ">(" + text + ");";
        return member.result.jni + " answer = " + text + ";";
    }
}
