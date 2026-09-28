package copper.bridge.gen;

/** Which virtual machine a declaration belongs to; the order is the generated enum's order. */
public enum Side {
    ART("Art"),
    JVM("Jvm");

    public final String cpp;

    Side(String cpp) {
        this.cpp = cpp;
    }

    public Side caller() {
        return this == ART ? JVM : ART;
    }
}
