package copper.bridge.gen;

/**
 * Where the annotations and the generated files live: both passes must agree on where an annotation may sit
 * and where their output goes, or one reads a declaration the other wrote code for somewhere else.
 */
public final class Packages {
    /** Every annotation, whichever subsystem reads it. Looked up by name, never by a class reference. */
    public static final String ANNOTATIONS = "copper.bridge.annotation";
    /** The logger the generated code reports through, named here because the generators write the calls. */
    public static final String LOG = "copper.bridge.util.Log";
    public static final String GENERATED = "copper.bridge.gen";

    private Packages() {
    }
}
