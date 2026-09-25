package copper.bridge.gen.bus;

import copper.bridge.gen.Packages;
import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the generated {@code Kinds} class: one constant per bus row, and the two lookups the log lines use.
 *
 * <p>The ids come from sorting the constant names, so an unchanged declaration keeps the id it always had.</p>
 */
final class JavaKinds {
    private JavaKinds() {
    }

    static String of(BusProcessor processor) {
        List<Template> constants = new ArrayList<>();
        for (Row row : processor.rows())
            constants.add(Template.of("""
                    static final int {{constant}} = {{id}};
                    """).with("constant", row.constant).with("id", row.id));

        List<Template> requests = new ArrayList<>();
        for (Row row : processor.rows()) {
            if (row.channel != Channel.POST || row.callbacks.isEmpty())
                continue;
            List<Template> answers = new ArrayList<>();
            for (Callback callback : row.callbacks)
                answers.add(Template.of("""
                        case {{constant}}:
                        """).with("constant", callback.answer.constant));
            requests.add(Template.of("""
                    {{answers}}
                        return {{constant}};
                    """)
                    .with("answers", Template.join(answers, "\n"))
                    .with("constant", row.constant));
        }

        List<Template> names = new ArrayList<>();
        for (Row row : processor.rows())
            names.add(Template.of("""
                    case {{constant}}: return "{{name}}";
                    """).with("constant", row.constant).with("name", row.name));

        return Template.of("""
                package {{package}};

                /** Generated. Do not edit. One constant per bus row; the ids come from sorting the
                 * constant names, so an unchanged declaration keeps the id it always had.
                 *
                 * <p>Package private, like the two call classes' use of it: an id is what one side passes
                 * to native, and nothing outside this package names one.</p> */
                final class Kinds {
                    /** No row at all: a direct call never travels. */
                    static final int NONE = -1;

                    {{constants}}

                    /** The request an answer belongs to, or NONE. Used for log lines only. */
                    static int requestOf(int kind) {
                        switch (kind) {
                            {{requests}}
                            default:
                                return NONE;
                        }
                    }

                    /** The bus name of a kind, for log lines only. */
                    static String nameOf(int kind) {
                        switch (kind) {
                            {{names}}
                            default: return "unknown(" + kind + ")";
                        }
                    }

                    private Kinds() {
                    }
                }
                """)
                .with("package", Packages.GENERATED)
                .with("constants", Template.join(constants, "\n"))
                .with("requests", Template.join(requests, "\n"))
                .with("names", Template.join(names, "\n"))
                .render();
    }
}
