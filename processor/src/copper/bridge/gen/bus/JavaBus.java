package copper.bridge.gen.bus;

import copper.bridge.gen.Packages;
import copper.bridge.gen.Side;
import copper.bridge.gen.Template;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes one side's bus class: the binding, the pump, and the native entry points the calls go through.
 * Binding only hands the instance to native and touches nothing the batch subsystem owns, so the two binds
 * stay separate calls and the caller names the half it hands over.
 */
final class JavaBus {
    private JavaBus() {
    }

    static String of(BusProcessor processor, Side side) {
        List<Row> rows = JavaGen.rowsFor(processor, side);
        String name = side == Side.JVM ? "JvmBus" : "ArtBus";

        // Named one by one from the declarations: a handler may sit in any package, and the generator
        // read the annotation there, so it knows where.
        List<Template> imports = new ArrayList<>();
        for (String owner : owners(rows))
            imports.add(JavaGen.importLine(owner));
        // Both markers are written here: every native names the entry that performs it, and the ART side
        // also marks the one method native calls back.
        imports.add(JavaGen.importLine(Packages.ANNOTATIONS + ".Native"));
        if (side == Side.ART)
            imports.add(JavaGen.importLine(Packages.ANNOTATIONS + ".UsedByNative"));

        List<Template> entries = new ArrayList<>();
        for (Map.Entry<String, Row> shape : Row.shapes(rows).entrySet()) {
            Row row = shape.getValue();
            entries.add(Template.of("""
                    @Native("gen::Bus::{{entry}}")
                    static native {{result}} {{entry}}(int kind{{params}});
                    """)
                    .with("entry", shape.getKey())
                    .with("result", row.result.java)
                    .with("params", row.params.isEmpty() ? "" : ", " + row.nativeParams()));
        }

        return Template.of("""
                package {{package}};

                {{imports}}

                /** Generated. Do not edit. The {{side}} side of the bus: binding, the pump, and one native per signature. */
                public final class {{name}} {

                    /**
                     * Binds one handler instance to the bus. Native takes it from here: it resolves the method ids
                     * of every kind the object's own type declares for this side, so nothing on this side has to
                     * know whether a type declares a bus handler at all, and a type that declares none is not a
                     * mistake.
                     */
                    @Native("bus::Handlers::Bind{{bind}}")
                    public static native void bind(Object handlers);

                    {{pump}}

                    // One entry per signature: the same signature is served once no matter how many
                    // messages use it. Package private, and reached only by the call class beside them.

                    {{entries}}

                    private {{name}}() {
                    }
                }
                """)
                .with("package", Packages.GENERATED)
                .with("imports", Template.join(imports, "\n"))
                .with("side", side == Side.JVM ? "JVM" : "ART")
                .with("name", name)
                .with("bind", side == Side.JVM ? "Jvm" : "Art")
                .with("pump", pump(side).render())
                .with("entries", Template.join(entries, "\n\n"))
                .render();
    }

    private static Template pump(Side side) {
        if (side == Side.JVM)
            return Template.of("""
                    /** Native: performs every queued event and answer on this thread. Call it once per
                     *  frame. */
                    @Native("bus::Dispatch::Pump")
                    public static native void pump();
                    """);
        // The ART side sleeps on a message loop instead of being pumped by a caller.
        return Template.of("""
                /** Native: performs every queued call here, on the ART main thread. */
                @Native("bus::Dispatch::Drain")
                private static native void drain();

                /** Native: completes every waiting caller with the neutral value. */
                @Native("bus::Waiters::Release")
                private static native void releaseWaiters();

                /** Called by native right after a call was queued, to wake the ART main thread up. */
                @UsedByNative(side = UsedByNative.Side.ART)
                @SuppressWarnings("unused")
                private static void postRequestPump() {
                    wake();
                }

                /** Starts the pump. Called once the activity exists. */
                public static void start() {
                    STARTED = true;
                    wake();
                }

                /**
                 * Stops the pump. Called once the activity is going away.
                 *
                 * <p>Nothing will perform the calls that are still queued, so a caller blocked on one
                 * of them has to be released instead of waiting for an answer that cannot come.</p>
                 */
                public static void stop() {
                    STARTED = false;
                    HANDLER.removeCallbacks(PUMP);
                    releaseWaiters();
                }

                /**
                 * How long the pump waits before looking again on its own.
                 *
                 * <p>Nothing should ever wait this long, because native posts a run of the pump the
                 * moment a call is queued. This slow tick only covers a wake-up that did not make it,
                 * so a call is late rather than lost.</p>
                 */
                private static final long PUMP_SAFETY_MILLIS = 250L;

                private static final android.os.Handler HANDLER =
                        new android.os.Handler(android.os.Looper.getMainLooper());
                private static final Runnable PUMP = ArtBus::pumpArt;

                private static volatile boolean STARTED;

                private static void wake() {
                    if (!STARTED)
                        return;
                    HANDLER.removeCallbacks(PUMP);
                    HANDLER.post(PUMP);
                }

                private static void pumpArt() {
                    try {
                        drain();
                    } finally {
                        if (STARTED)
                            HANDLER.postDelayed(PUMP, PUMP_SAFETY_MILLIS);
                    }
                }
                """);
    }

    private static List<String> owners(List<Row> rows) {
        Set<String> found = new LinkedHashSet<>();
        for (Row row : rows) {
            // An answer row comes from the model, not from a method, so it has no owner of its own.
            if (row.owner.startsWith(Packages.GENERATED))
                continue;
            found.add(row.owner);
        }
        return new ArrayList<>(found);
    }
}
