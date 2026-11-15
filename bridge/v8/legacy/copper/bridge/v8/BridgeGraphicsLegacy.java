package copper.bridge.v8;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import arc.Graphics.DisplayMode;
import arc.Graphics.Monitor;

/**
 * The floor's graphics shape: 127 still declares the Monitor and display-mode group, which 159 removed. It is
 * compiled against the floor epoch and selected only when the running game has {@code Monitor}.
 */
@CompatRange(floor = "11247", ceiling = "11247")
public class BridgeGraphicsLegacy extends BridgeGraphics {

    @BridgeOverride
    public Monitor getPrimaryMonitor() {
        return null;
    }

    @BridgeOverride
    public Monitor getMonitor() {
        return null;
    }

    @BridgeOverride
    public Monitor[] getMonitors() {
        return new Monitor[0];
    }

    @BridgeOverride
    public DisplayMode[] getDisplayModes() {
        return new DisplayMode[0];
    }

    @BridgeOverride
    public DisplayMode[] getDisplayModes(Monitor monitor) {
        return new DisplayMode[0];
    }

    @BridgeOverride
    public DisplayMode getDisplayMode() {
        return null;
    }

    @BridgeOverride
    public DisplayMode getDisplayMode(Monitor monitor) {
        return null;
    }

    @BridgeOverride
    public boolean setFullscreenMode(DisplayMode displayMode) {
        return false;
    }
}
