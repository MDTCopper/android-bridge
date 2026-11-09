package copper.bridge.compat.refs;

import java.util.regex.*;

/** One member reference into a previous bridge branch, which the check follows to that branch's class file. */
public final class BridgeRef {
    private static final Pattern OWNER = Pattern.compile("copper/bridge/v(\\d+)/.*");

    public final String sourceClass;
    public final String owner;
    public final String name;
    public final String descriptor;
    public final int branch;

    public BridgeRef(String sourceClass, String owner, String name, String descriptor, int branch) {
        this.sourceClass = sourceClass;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.branch = branch;
    }

    /** The branch number an owner belongs to, or 0 when it is not a bridge class. */
    public static int branchOf(String owner) {
        if (owner == null)
            return 0;
        Matcher matcher = OWNER.matcher(owner);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public String key() {
        return owner + "." + name + ":" + descriptor;
    }
}
