package copper.bridge.gen.batch;

import copper.bridge.gen.Names;
import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the per-side binding: the instances an accessor holds, and how a bound object is matched to one. An
 * accessor holds only the channels it receives; Classes are held by name, because the other virtual machine's
 * classes do not resolve here.
 */
final class BatchRegistry {
    private BatchRegistry() {
    }

    static Template of(List<Schema> schemas) {
        List<Template> declared = new ArrayList<>();
        for (Schema schema : schemas)
            declared.add(Template.of("""
                    "{{owner}}",
                    """).with("owner", schema.owner));

        List<Template> bounds = new ArrayList<>();
        for (Schema schema : schemas)
            bounds.add(Template.of("""
                    /** The instance bound to the '{{accessor}}' channel, or null while nothing is bound. */
                    private static volatile {{type}} bound{{Accessor}};

                    """)
                    .with("accessor", schema.accessor)
                    .with("type", Names.simpleName(schema.owner))
                    .with("Accessor", Names.capitalize(schema.accessor)));

        List<Template> assignments = new ArrayList<>();
        for (Schema schema : schemas)
            assignments.add(Template.of("""
                    if (declared.equals("{{owner}}"))
                        bound{{Accessor}} = ({{type}}) handlers;
                    """)
                    .with("owner", schema.owner)
                    .with("Accessor", Names.capitalize(schema.accessor))
                    .with("type", Names.simpleName(schema.owner)));

        return Template.of("""
                    /** The declaration types this side's channels are declared by, as Java names them. */
                    private static final String[] DECLARED = {
                            {{declared}}
                    };

                    /** The declaration types that already have an instance; one instance each. */
                    private static final Set<String> BOUND = ConcurrentHashMap.newKeySet();

                    {{bounds}}

                    /**
                     * Binds the instance this side's channels are served by. The bus half is not consulted here: a
                     * type that declares no channel of this side simply does not match, and whether it has bus
                     * handlers is native's business.
                     *
                     * @return whether the object declares one of this side's schemas
                     */
                    public static boolean bind(Object handlers) {
                        String declared = declared(handlers.getClass());
                        if (declared == null)
                            return false;
                        if (!BOUND.add(declared)) {
                            Log.warn("bridge: already bound: " + declared);
                            return false;
                        }
                        {{assignments}}
                        return true;
                    }

                    /** The schema a type declares, or null when it declares none of this side's. */
                    private static String declared(Class<?> type) {
                        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                            String found = declared(current, new HashSet<>());
                            if (found != null)
                                return found;
                        }
                        return null;
                    }

                    private static String declared(Class<?> type, Set<Class<?>> seen) {
                        if (!seen.add(type))
                            return null;
                        if (Arrays.asList(DECLARED).contains(type.getName()))
                            return type.getName();
                        for (Class<?> iface : type.getInterfaces()) {
                            String found = declared(iface, seen);
                            if (found != null)
                                return found;
                        }
                        return null;
                    }

                    """)
                .with("declared", Template.join(declared, "\n"))
                .with("bounds", Template.join(bounds, "\n\n"))
                .with("assignments", Template.join(assignments, "\n"));
    }
}
