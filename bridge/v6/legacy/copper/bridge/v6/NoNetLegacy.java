package copper.bridge.v6;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.Net;

import copper.bridge.gen.*;

/** The abstract Net of 6320; the class becomes concrete at 6415. */
@CompatRange(floor = "6320")
public class NoNetLegacy extends Net {

    @BridgeOverride
    @Override
    public boolean openURI(String uri) {
        return JvmCall.openUri(uri);
    }
}
