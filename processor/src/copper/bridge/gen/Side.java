package copper.bridge.gen;

/** Which virtual machine a declaration belongs to. The order is the generated enum's order. */
public enum Side {
    ART("Art"),
    JVM("Jvm");

    /** The enumerator the generated Java and C++ use. */
    public final String cpp;

    Side(String cpp) {
        this.cpp = cpp;
    }

    /** The other side: the declaring side owns the handler, so the caller is always its opposite. */
    public Side caller() {
        return this == ART ? JVM : ART;
    }
}
