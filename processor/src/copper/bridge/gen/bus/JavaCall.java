package copper.bridge.gen.bus;

import copper.bridge.gen.Names;
import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
import copper.bridge.gen.Template;
import copper.bridge.gen.TypeRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes one side's call class: every row that side calls, plus the delivery of the answers it is owed.
 */
final class JavaCall {
    private JavaCall() {
    }

    static String of(BusProcessor processor, Side side) {
        List<Row> rows = JavaGen.rowsFor(processor, side);
        List<Row> requests = new ArrayList<>();
        for (Row row : rows) {
            if (!row.callbacks.isEmpty())
                requests.add(row);
        }
        List<Row> answers = new ArrayList<>();
        for (Row row : processor.rows()) {
            if (row.channel == Channel.ANSWER && row.ownerSide == side)
                answers.add(row);
        }

        String name = BusProcessor.callClass(side);
        // The annotations and the logger are named outright because this file writes them; every
        // other name comes from a declaration, whose package the generator read rather than assumed.
        List<Template> imports = new ArrayList<>();
        for (String type : types(rows))
            imports.add(JavaGen.importLine(type));

        List<Template> calls = new ArrayList<>();
        for (Row row : rows)
            calls.add(callMethod(row));

        return Template.of("""
                package {{package}};

                import {{annotations}}.*;
                import {{log}};
                {{imports}}

                /** Generated. Do not edit. The calls the {{side}} side makes: one method per call, one native per signature, so a new call of an
                 * existing shape adds no native method. A call that returns a value waits for its own
                 * answer; a call that returns void is queued and forgotten. */
                public final class {{name}} {

                    {{calls}}

                    {{answers}}
                    private {{name}}() {
                    }
                }
                """)
                .with("package", Packages.GENERATED)
                .with("annotations", Packages.ANNOTATIONS)
                .with("log", Packages.LOG)
                .with("imports", Template.join(imports, "\n"))
                .with("side", side == Side.JVM ? "JVM" : "ART")
                .with("name", name)
                .with("calls", Template.join(calls, "\n\n"))
                .with("answers", answers.isEmpty() ? "" : answers(requests).render())
                .render();
    }

    /** One public wrapper: what a caller reads, over the native entry of its signature. */
    private static Template callMethod(Row row) {
        Template body = row.callbacks.isEmpty() ? direct(row, !row.result.isVoid) : asynchronous(row);
        return Template.of("""
                /** {{summary}} */
                @{{marker}}
                public static {{return}} {{name}}({{params}}) {
                    {{body}}
                }
                """)
                .with("summary", summary(row))
                .with("marker", marker(row))
                .with("return", returnType(row))
                .with("name", row.name)
                .with("params", row.javaParams())
                .with("body", body.render());
    }

    /** The body of a call that does not come back later: it is performed, or queued and forgotten. */
    private static Template direct(Row row, boolean withReturn) {
        return Template.of("""
                {{return}}{{entry}}(Kinds.{{constant}}{{arguments}});
                """)
                .with("return", withReturn ? "return " : "")
                .with("entry", entry(row))
                .with("constant", row.constant)
                .with("arguments", row.params.isEmpty() ? "" : ", " + String.join(", ", row.paramNames));
    }

    /** The body of a call that comes back later: the holder is registered, then the call is queued. */
    private static Template asynchronous(Row row) {
        String holder = Names.holder(row.name);
        List<String> arguments = new ArrayList<>();
        for (int i = 1; i < row.params.size(); i++)
            arguments.add(row.paramNames.get(i));

        return Template.of("""
                {{holder}} holder = new {{holder}}();
                long id = nextRequestId();
                PENDING.put(id, holder);
                {{entry}}(Kinds.{{constant}}, id{{arguments}});
                return holder;
                """)
                .with("holder", holder)
                .with("entry", entry(row))
                .with("constant", row.constant)
                .with("arguments", arguments.isEmpty() ? "" : ", " + String.join(", ", arguments));
    }

    /** Everything the answers of one call class need: the bookkeeping, the deliveries and the holders. */
    private static Template answers(List<Row> requests) {
        // One delivery method per payload shape: the kind is what tells two outcomes of the same
        // request apart, and the value is the single thing an outcome carries.
        Map<String, List<Callback>> shapes = new LinkedHashMap<>();
        for (Row request : requests) {
            for (Callback callback : request.callbacks)
                shapes.computeIfAbsent(callback.payload == null ? "" : callback.payload.java,
                        key -> new ArrayList<>()).add(callback);
        }

        List<Template> deliveries = new ArrayList<>();
        for (List<Callback> group : shapes.values())
            deliveries.add(delivery(group));

        List<Template> holders = new ArrayList<>();
        for (Row request : requests)
            holders.add(holder(request));

        return Template.of("""
                // In-flight requests, one entry per call: two calls to the same request do not
                // interfere, and an answer is matched by the id it carries rather than by its kind.

                private static final ConcurrentHashMap<Long, Object> PENDING = new ConcurrentHashMap<>();

                private static final AtomicLong NEXT_ID = new AtomicLong(1L);

                private static long nextRequestId() {
                    return NEXT_ID.getAndIncrement();
                }

                {{deliveries}}

                /** One value of an outcome. */
                @FunctionalInterface
                public interface Cons<T> {
                    void get(T value);
                }

                {{holders}}""")
                .with("deliveries", Template.join(deliveries, "\n\n"))
                .with("holders", Template.join(holders, "\n\n"));
    }

    /** One delivery method: the outcomes that carry the same value are answered in one switch. */
    private static Template delivery(List<Callback> group) {
        List<Template> cases = new ArrayList<>();
        for (Callback callback : group) {
            cases.add(Template.of("""
                    case Kinds.{{constant}}:
                        (({{holder}}) holder).{{fire}}({{value}});
                        break;
                    """)
                    .with("constant", callback.answer.constant)
                    .with("holder", Names.holder(callback.request.name))
                    .with("fire", callback.fire())
                    .with("value", callback.payload == null ? "" : "value"));
        }

        return Template.of("""
                /** Called by the native pump with the id an answer carries. */
                static void deliver(long request, int kind{{value}}) {
                    Object holder = PENDING.remove(request);
                    if (holder == null) {
                        Log.warn("bridge: answer for an unknown request " + request
                                + " (" + Kinds.nameOf(kind) + ")");
                        return;
                    }
                    switch (kind) {
                        {{cases}}
                        default:
                            Log.warn("bridge: unexpected answer kind: " + Kinds.nameOf(kind));
                            break;
                    }
                }
                """)
                .with("value", group.get(0).payload == null ? "" : ", " + group.get(0).payload.java + " value")
                .with("cases", Template.join(cases, "\n"));
    }

    /** The holder of one asynchronous call's outcomes: one field, one setter and one fire per outcome. */
    private static Template holder(Row request) {
        String holder = Names.holder(request.name);

        List<Template> fields = new ArrayList<>();
        for (Callback callback : request.callbacks)
            fields.add(Template.of("""
                    private {{type}} {{field}};
                    """).with("type", callbackType(callback)).with("field", field(callback)));

        List<Template> setters = new ArrayList<>();
        for (Callback callback : request.callbacks)
            setters.add(Template.of("""
                    /** Registers the callback of the '{{outcome}}' outcome; leaving it out means this outcome does nothing. */
                    public {{holder}} {{setter}}({{type}} callback) {
                        {{field}} = callback;
                        return this;
                    }
                    """)
                    .with("outcome", callback.name)
                    .with("holder", holder)
                    .with("setter", callback.setter())
                    .with("type", callbackType(callback))
                    .with("field", field(callback)));

        List<Template> fires = new ArrayList<>();
        for (Callback callback : request.callbacks)
            fires.add(Template.of("""
                    void {{fire}}({{payload}}) {
                        if ({{field}} != null)
                            {{call}}
                    }
                    """)
                    .with("fire", callback.fire())
                    .with("payload", callback.payload == null ? "" : callback.payload.java + " value")
                    .with("field", field(callback))
                    .with("call", field(callback) + (callback.payload == null ? ".run();" : ".get(value);")));

        return Template.of("""
                /** Generated. The outcomes of one {@link {{call}}#{{name}}} call; one instance per call. */
                public static final class {{holder}} {
                    {{fields}}

                    {{holder}}() {
                    }

                    {{setters}}

                    {{fires}}
                }
                """)
                .with("call", BusProcessor.callClass(request.callSide))
                .with("name", request.name)
                .with("holder", holder)
                .with("fields", Template.join(fields, "\n"))
                .with("setters", Template.join(setters, "\n\n"))
                .with("fires", Template.join(fires, "\n\n"));
    }

    private static String summary(Row row) {
        switch (row.channel) {
            case DIRECT:
                return "Runs on the calling thread and answers now.";
            case EVENT:
                return "Tells the other side that " + row.name + " happened.";
            case ANSWER:
                return "The answer '" + row.name + "', carrying back the request it belongs to.";
            default:
                if (!row.callbacks.isEmpty())
                    return "Queues the call; the outcomes arrive later, on the other side's own thread.";
                return row.waits
                        ? "Queues the call and waits for the value it returns."
                        : "Queues the call to the other side's main thread.";
        }
    }

    private static String marker(Row row) {
        switch (row.channel) {
            case DIRECT:
                return "Direct";
            case EVENT:
            case ANSWER:
                return "Event";
            default:
                return "Post";
        }
    }

    /** The native entry a row's call goes through, qualified by the bus class that declares it. */
    private static String entry(Row row) {
        return (row.callSide == Side.JVM ? "JvmBus" : "ArtBus") + "." + row.entry();
    }

    /**
     * The imports a generated file needs: every name comes from a declaration, never from a package
     * constant, so a declaration is free to sit anywhere.
     */
    private static List<String> types(List<Row> rows) {
        Set<String> found = new LinkedHashSet<>();
        found.add("java.util.concurrent.*");
        found.add("java.util.concurrent.atomic.*");
        for (Row row : rows) {
            for (TypeRef param : row.params)
                add(found, param);
            add(found, row.result);
            for (Callback callback : row.callbacks)
                if (callback.payload != null)
                    add(found, callback.payload);
        }
        return new ArrayList<>(found);
    }

    /** Records the import one type reference needs: only a qualified name has a package to import. */
    private static void add(Set<String> found, TypeRef type) {
        if (type == null || type.java.indexOf('.') < 0)
            return;
        found.add(type.java.substring(0, type.java.lastIndexOf('.')));
    }

    private static String returnType(Row row) {
        if (!row.callbacks.isEmpty())
            return Names.holder(row.name);
        return row.result.java;
    }

    private static String callbackType(Callback callback) {
        return callback.payload == null ? "Runnable" : "Cons<" + callback.payload.java + ">";
    }

    private static String field(Callback callback) {
        return callback.setter();
    }
}
