package copper.bridge.v10;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import arc.graphics.Cursor;
import arc.graphics.Cursor.SystemCursor;
import arc.graphics.Pixmap;

/** The ceiling graphics shape: 28063 moved {@code Graphics.Cursor} to {@code arc.graphics.Cursor}. */
@CompatRange(floor = "28063")
public class BridgeGraphicsCurrent extends BridgeGraphics {

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
