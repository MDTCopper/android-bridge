package copper.bridge.v2;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.util.Clipboard;

import copper.bridge.gen.*;

/** The floor's {@link Clipboard}: the text methods ART already exposes, behind the interface the game still asks for. */
@CompatRange(floor = "v65", ceiling = "v65")
final class ClipboardBridge implements Clipboard {

    @BridgeOverride
    public String getContents() {
        return JvmCall.getClipboardText();
    }

    @BridgeOverride
    public void setContents(String text) {
        JvmCall.setClipboardText(text);
    }
}
