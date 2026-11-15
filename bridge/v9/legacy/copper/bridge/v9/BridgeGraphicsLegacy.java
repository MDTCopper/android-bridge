package copper.bridge.v9;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import arc.Graphics.Cursor;
import arc.Graphics.Cursor.SystemCursor;
import arc.graphics.Pixmap;

/**
 * The floor's graphics shape: 27930 still has {@code arc.Graphics.Cursor}, which 28063 moved to
 * {@code arc.graphics.Cursor}. It is compiled against the floor epoch and selected only when the running game has
 * the old cursor type.
 */
@CompatRange(floor = "27930", ceiling = "27930")
public class BridgeGraphicsLegacy extends BridgeGraphics {

    @BridgeOverride
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot) {
        return null;
    }

    @BridgeOverride
    protected void setCursor(Cursor cursor) {
    }

    @BridgeOverride
    protected void setSystemCursor(SystemCursor systemCursor) {
    }
}
