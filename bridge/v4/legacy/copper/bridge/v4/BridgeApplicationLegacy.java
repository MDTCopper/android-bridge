package copper.bridge.v4;

import copper.bridge.annotation.CompatRange;

import io.anuke.arc.Net;

/** The 5695 application shape: the old input and Net implementations. */
@CompatRange(floor = "5695", ceiling = "5695")
public class BridgeApplicationLegacy extends BridgeApplication {

    @Override
    protected BridgeInput createInput() {
        return new BridgeInputLegacy();
    }

    @Override
    protected Net createNet() {
        return new NoNetLegacy();
    }
}
