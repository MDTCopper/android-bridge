package copper.bridge.compat.check;

/** One comparison range: the jar refs at each end. */
public final class Range {
    public final String floor;
    public final String ceiling;

    public Range(String floor, String ceiling) {
        this.floor = floor;
        this.ceiling = ceiling;
    }
}
