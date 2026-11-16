package copper.bridge.v4;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.*;
import io.anuke.arc.func.*;

import copper.bridge.gen.*;

/** The 5695 text-input shape: {@code accepted} is a Cons. */
@CompatRange(floor = "5695", ceiling = "5695")
public class BridgeInputLegacy extends BridgeInput {

    @BridgeOverride
    public void getTextInput(Input.TextInput info) {
        JvmCall.textInput(info.title, "", info.text, false, info.multiline, info.maxLength, false)
                .onResult(value -> info.accepted.get(value))
                .onCanceled(info.canceled);
    }
}
