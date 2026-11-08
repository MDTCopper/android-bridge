package copper.bridge.compat.check;

/** One comparison range: the game and arc commits at each end, and whether that end's arc is in the game tree. */
public final class Range {
    public final String gameFloor;
    public final String gameCeiling;
    public final String arcFloor;
    public final String arcCeiling;
    public final boolean arcFloorMerged;
    public final boolean arcCeilingMerged;

    public Range(String gameFloor, String gameCeiling, String arcFloor, String arcCeiling,
                 boolean arcFloorMerged, boolean arcCeilingMerged) {
        this.gameFloor = gameFloor;
        this.gameCeiling = gameCeiling;
        this.arcFloor = arcFloor;
        this.arcCeiling = arcCeiling;
        this.arcFloorMerged = arcFloorMerged;
        this.arcCeilingMerged = arcCeilingMerged;
    }
}
