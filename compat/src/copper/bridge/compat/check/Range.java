package copper.bridge.compat.check;

/** One comparison range: the refs at each end, with the arc side kept separately for the source check. */
public final class Range {
    public final String floor;
    public final String ceiling;
    /** Arc refs for the source check; null when the range has no separate arc side. */
    public final String arcFloor;
    public final String arcCeiling;

    public Range(String floor, String ceiling) {
        this(floor, ceiling, null, null);
    }

    public Range(String floor, String ceiling, String arcFloor, String arcCeiling) {
        this.floor = floor;
        this.ceiling = ceiling;
        this.arcFloor = arcFloor;
        this.arcCeiling = arcCeiling;
    }
}
