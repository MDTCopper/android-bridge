package copper.bridge.v2;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.*;
import io.anuke.arc.util.*;

/**
 * The floor's application shape: 65 still declares {@code Application.getClipboard}, which 83 replaced with the text
 * methods this class inherits. It is compiled against the floor epoch and selected only when the running game has
 * {@code Clipboard}.
 */
@CompatRange(floor = "v65", ceiling = "v65")
public class BridgeApplicationLegacy extends BridgeApplication {

    @BridgeOverride
    public Clipboard getClipboard() {
        return new ClipboardBridge();
    }

    @Override
    protected Net createNet() {
        return new NoNetLegacy();
    }
}
