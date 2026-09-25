package copper.bridge.gen;

/**
 * Where the annotations and the generated files live.
 *
 * <p>Both passes must agree on where an annotation may sit and where their output goes, or one
 * reads a declaration the other wrote code for somewhere else.</p>
 * <p>A <em>declaration</em>'s own package is deliberately not named: it is data read from the
 * declaration, so a constant would only be a claim the next declaration can contradict.</p>
 */
public final class Packages {
    /** Every annotation, whichever subsystem reads it. Looked up by name, never by a class reference. */
    public static final String ANNOTATIONS = "copper.bridge.annotation";
    /** The logger the generated code reports through, named here because the generators write the calls. */
    public static final String LOG = "copper.bridge.util.Log";
    /** The package the generated Java lands in. */
    public static final String GENERATED = "copper.bridge.gen";

    private Packages() {
    }
}
