package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.gen.JvmCall;

import com.badlogic.gdx.utils.Clipboard;

/** libGDX's clipboard, answered by the ART side, which owns the Android clipboard. */
public class BridgeClipboard implements Clipboard {

    @Override
    @BridgeOverride
    public String getContents() {
        return JvmCall.getClipboardText();
    }

    @Override
    @BridgeOverride
    public void setContents(String content) {
        JvmCall.setClipboardText(content);
    }
}
