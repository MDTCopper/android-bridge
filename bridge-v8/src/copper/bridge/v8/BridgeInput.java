package copper.bridge.v8;

import arc.Input.TextInput;
import copper.bridge.gen.*;

/**
 * v6's input plus the two text-dialog fields that arrive inside this branch's range. The fields are read by
 * reflection because the range's floor does not declare them.
 */
public class BridgeInput extends copper.bridge.v6.BridgeInput {
    @Override
    public void getTextInput(TextInput info) {
        JvmCall.textInput(info.title, message(info), info.text, info.numeric, info.multiline,
                info.maxLength, allowEmpty(info))
                .onResult(value -> info.accepted.get(value))
                .onCanceled(info.canceled);
    }

    /** The hint line, where the running Input declares it; the field is absent before 147. */
    private static String message(TextInput info) {
        try {
            Object value = TextInput.class.getField("message").get(info);
            return value == null ? "" : (String)value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Whether an empty answer is accepted, where the running Input declares the flag; before 130 it was refused. */
    private static boolean allowEmpty(TextInput info) {
        try {
            return (boolean)TextInput.class.getField("allowEmpty").get(info);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
