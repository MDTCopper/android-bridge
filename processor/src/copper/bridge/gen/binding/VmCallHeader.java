package copper.bridge.gen.binding;

import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code gen/vmcall.h}: one declaration per member native reaches for. Every name in the namespace is derived
 * from a member's own name, so nothing else may live there.
 */
final class VmCallHeader {
    private VmCallHeader() {
    }

    static String of(BindingProcessor processor) {
        List<Template> members = new ArrayList<>();
        String owner = null;
        for (Reverse member : processor.reverses()) {
            if (!member.owner.equals(owner)) {
                owner = member.owner;
                members.add(Template.of("""
                        // --- {{owner}} ---
                        """).with("owner", owner));
            }
            members.add(Template.of("""
                    /** Calls {{owner}}.{{method}}({{params}}){{environment}}{{answer}}. */
                    Binding::Outcome {{entry}}({{parameters}});
                    """)
                    .with("owner", member.owner)
                    .with("method", member.method)
                    .with("params", member.declaredParams())
                    .with("environment", member.bothSides() ? ", in the caller's own environment"
                                                            : ", in " + member.side + "'s environment")
                    .with("answer", member.result.isVoid ? "" : ", writing the answer through the pointer")
                    .with("entry", member.entry)
                    .with("parameters", String.join(", ", member.parameters())));
        }

        return Template.of("""
                // Generated. Do not edit.
                #pragma once

                #include <jni.h>

                #include "gen/binding.h"

                // The calls native makes into the VM: one entry per member Java marks with @UsedByNative. Nothing
                // else lives in this namespace, because every name below is derived from a member's own name and a
                // name of any other kind could collide with one of them; the vocabulary and the tables are in
                // gen::Binding for that reason. What each call is for is not generated: the declaration says which
                // member, and the annotation which environment and whether the caller is coming back.

                namespace copper::bridge::gen::VmCall {

                    {{members}}

                } // namespace copper::bridge::gen::VmCall
                """)
                .with("members", Template.join(members, "\n\n"))
                .render();
    }
}
