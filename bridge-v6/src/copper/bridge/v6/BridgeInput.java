package copper.bridge.v6;

import arc.*;
import arc.input.*;
import copper.bridge.backend.input.InputEngine;
import copper.bridge.jvm.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;
import copper.bridge.util.WireCharBuffer;
import java.util.*;

/** The game's input, fed from the packed batches ART produces; polled and read on the game loop thread alone. */
public class BridgeInput extends Input implements copper.bridge.v1.InputHost {
    private static final KeyCode[] KEYS = KeyCode.values();

    // v6..v8 share this class; the enum constants were renamed without changing their order.
    private static final Peripheral ONSCREEN_KEYBOARD = Peripheral.values()[1]; //onscreenKeyboard
    private static final Peripheral MULTITOUCH_SCREEN = Peripheral.values()[2]; //multitouchScreen
    private static final Peripheral VIBRATOR = Peripheral.values()[5]; //vibrator
    private static final Peripheral PRESSURE = Peripheral.values()[8]; //pressure
    private static final Orientation LANDSCAPE = Orientation.values()[0]; //landscape
    private static final Orientation PORTRAIT = Orientation.values()[1]; //portrait

    /** Absent at the range's floor; the lambda calls the newer postUpdate directly. */
    private final Runnable postUpdate = MethodLambdas.boundRunnable(keyboard, "postUpdate");

    private final InputEngine engine;
    private final boolean[] downState = new boolean[KeyCode.all.length];

    public BridgeInput() {
        engine = new InputEngine(new Sink(), () -> Core.graphics.getHeight());
    }

    /** Maps the backend's raw pointer/key events to arc's input multiplexer. */
    private final class Sink implements InputEngine.Sink {
        @Override
        public void touchDown(int pointer, int x, int y) {
            inputMultiplexer.touchDown(x, y, pointer, KEYS[21]); //mouseLeft
        }

        @Override
        public void touchUp(int pointer, int x, int y) {
            inputMultiplexer.touchUp(x, y, pointer, KEYS[21]); //mouseLeft
        }

        @Override
        public void pointerMove(int pointer, int x, int y) {
            if (pointer == -1)
                inputMultiplexer.mouseMoved(x, y);
            else
                inputMultiplexer.touchDragged(x, y, pointer);
        }

        @Override
        public void scroll(float amount) {
            inputMultiplexer.scrolled(0f, amount);
        }

        @Override
        public void keyDown(int deviceCode, int repeat) {
            if (repeat > 0) return;

            KeyCode key = KeyMap.getKeyCode(deviceCode);
            int index = key.ordinal();
            if (downState[index]) return;

            downState[index] = true;
            inputMultiplexer.keyDown(key);
        }

        @Override
        public void keyUp(int deviceCode, WireCharBuffer chars) {
            KeyCode key = KeyMap.getKeyCode(deviceCode);
            int index = key.ordinal();
            if (!downState[index]) return;

            downState[index] = false;
            inputMultiplexer.keyUp(key);
            type(chars);
        }

        @Override
        public void text(WireCharBuffer chars) {
            type(chars);
        }

        private void type(WireCharBuffer chars) {
            for (int i = 0; i < chars.length(); i++) {
                inputMultiplexer.keyTyped(chars.charAt(i));
            }
        }
    }

    /** One back tap through the normal input path, used when no dialog is open. */
    public void backTap() {
        inputMultiplexer.keyDown(KEYS[44]); //back
        inputMultiplexer.keyUp(KEYS[44]); //back
    }

    /** Drains every pending frame and replays it, so a swipe sampled faster than the frame rate arrives whole. */
    public void processEvents() {
        engine.processEvents();
    }

    /** Not a loop over {@code Input.devices}: arc removes that field in v152, and the keyboard is its only device. */
    public void processDevices() {
        if (postUpdate != null)
            postUpdate.run();
    }

    public void onPause() {
        clearState();
    }

    public void onResume() {
        clearState();
    }

    private void clearState() {
        Arrays.fill(downState, false);
        engine.clearPointers();
    }

    //region text input and other peripherals

    /** Shows the text input dialog ART owns; the answer arrives on the game loop. */
    @Override
    public void getTextInput(TextInput info) {
        // the 6 line's TextInput has no hint line and no empty-answer flag, so the dialog gets neither
        JvmCall.textInput(info.title, "", info.text, info.numeric, info.multiline,
                info.maxLength, false)
                .onResult(value -> info.accepted.get(value))
                .onCanceled(info.canceled);
    }

    @Override
    public void setOnscreenKeyboardVisible(boolean visible) {
        JvmCall.onscreenKeyboard(visible);
    }

    @Override
    public void vibrate(int milliseconds) {
        JvmCall.vibrate(milliseconds);
    }

    @Override
    public void vibrate(long[] pattern, int repeat) {
        JvmCall.vibrate(pattern, repeat);
    }

    @Override
    public void cancelVibrate() {
        JvmCall.cancelVibrate();
    }

    /** Reports the peripherals this bridge can serve. The vibrator is answered by ART rather than guessed, and nothing
     *  else is bridged: claiming one would hand the game a peripheral that never answers. */
    @Override
    public boolean isPeripheralAvailable(Peripheral peripheral) {
        if (peripheral == ONSCREEN_KEYBOARD) return true;
        if (peripheral == MULTITOUCH_SCREEN) return true;
        if (peripheral == VIBRATOR) return JvmCall.isVibratorAvailable();
        return peripheral == PRESSURE;
    }

    /** The display's rotation in degrees, translated on the ART side, which owns the constants. */
    @Override
    public int getRotation() {
        return JvmCall.screenRotationDegrees();
    }

    /** Whether the device's display is natively landscape, asked of ART and not cached. */
    @Override
    public Orientation getNativeOrientation() {
        return JvmCall.isNativeLandscape() ? LANDSCAPE : PORTRAIT;
    }
    //endregion

    //region polling

    @Override
    public int mouseX() {
        return engine.mouseX(0);
    }

    @Override
    public int mouseX(int pointer) {
        return engine.mouseX(pointer);
    }

    @Override
    public int mouseY() {
        return engine.mouseY(0);
    }

    @Override
    public int mouseY(int pointer) {
        return engine.mouseY(pointer);
    }

    @Override
    public int deltaX() {
        return engine.deltaX(0);
    }

    @Override
    public int deltaX(int pointer) {
        return engine.deltaX(pointer);
    }

    @Override
    public int deltaY() {
        return engine.deltaY(0);
    }

    @Override
    public int deltaY(int pointer) {
        return engine.deltaY(pointer);
    }

    @Override
    public boolean isTouched() {
        return engine.isTouched();
    }

    @Override
    public boolean isTouched(int pointer) {
        return engine.isTouched(pointer);
    }

    @Override
    public boolean justTouched() {
        return engine.justTouched();
    }

    @Override
    public float getPressure(int pointer) {
        return engine.pressure(pointer);
    }

    @Override
    public long getCurrentEventTime() {
        return engine.currentEventTime();
    }

    //endregion

    /** The Android key code table, ported from arc's Android backend so the game sees the same {@link KeyCode}. */
    private static class KeyMap {
        static KeyCode getKeyCode(int key) {
            switch (key) {
                case -1: return KEYS[27]; //anyKey
                case 7: return KEYS[28]; //num0
                case 8: return KEYS[29]; //num1
                case 9: return KEYS[30]; //num2
                case 10: return KEYS[31]; //num3
                case 11: return KEYS[32]; //num4
                case 12: return KEYS[33]; //num5
                case 13: return KEYS[34]; //num6
                case 14: return KEYS[35]; //num7
                case 15: return KEYS[36]; //num8
                case 16: return KEYS[37]; //num9
                case 29: return KEYS[38]; //a
                case 57: return KEYS[39]; //altLeft
                case 58: return KEYS[40]; //altRight
                case 75: return KEYS[41]; //apostrophe
                case 77: return KEYS[42]; //at
                case 30: return KEYS[43]; //b
                case 4: return KEYS[44]; //back
                case 73: return KEYS[45]; //backslash
                case 31: return KEYS[46]; //c
                case 5: return KEYS[47]; //call
                case 27: return KEYS[48]; //camera
                case 28: return KEYS[49]; //clear
                case 55: return KEYS[50]; //comma
                case 32: return KEYS[51]; //d
                case 67: return KEYS[53]; //backspace
                case 112: return KEYS[54]; //forwardDel
                case 23: return KEYS[60]; //center
                case 20: return KEYS[61]; //down
                case 21: return KEYS[62]; //left
                case 22: return KEYS[63]; //right
                case 19: return KEYS[64]; //up
                case 33: return KEYS[65]; //e
                case 6: return KEYS[66]; //endcall
                case 66: return KEYS[67]; //enter
                case 65: return KEYS[68]; //envelope
                case 70: return KEYS[69]; //equals
                case 34: return KEYS[71]; //f
                case 80: return KEYS[72]; //focus
                case 35: return KEYS[73]; //g
                case 68: return KEYS[74]; //backtick
                case 36: return KEYS[75]; //h
                case 79: return KEYS[76]; //headsetHook
                case 3: return KEYS[77]; //home
                case 37: return KEYS[78]; //i
                case 38: return KEYS[79]; //j
                case 39: return KEYS[80]; //k
                case 40: return KEYS[81]; //l
                case 71: return KEYS[82]; //leftBracket
                case 41: return KEYS[83]; //m
                case 90: return KEYS[84]; //mediaFastForward
                case 87: return KEYS[85]; //mediaNext
                case 85: return KEYS[86]; //mediaPlayPause
                case 88: return KEYS[87]; //mediaPrevious
                case 89: return KEYS[88]; //mediaRewind
                case 86: return KEYS[89]; //mediaStop
                case 82: return KEYS[90]; //menu
                case 69: return KEYS[91]; //minus
                case 91: return KEYS[92]; //mute
                case 42: return KEYS[93]; //n
                case 83: return KEYS[94]; //notification
                case 78: return KEYS[95]; //num
                case 43: return KEYS[96]; //o
                case 44: return KEYS[97]; //p
                case 56: return KEYS[98]; //period
                case 81: return KEYS[99]; //plus
                case 18: return KEYS[100]; //pound
                case 26: return KEYS[101]; //power
                case 45: return KEYS[102]; //q
                case 46: return KEYS[103]; //r
                case 72: return KEYS[104]; //rightBracket
                case 47: return KEYS[105]; //s
                case 84: return KEYS[106]; //search
                case 74: return KEYS[107]; //semicolon
                case 59: return KEYS[108]; //shiftLeft
                case 60: return KEYS[109]; //shiftRight
                case 76: return KEYS[110]; //slash
                case 1: return KEYS[111]; //softLeft
                case 2: return KEYS[112]; //softRight
                case 62: return KEYS[113]; //space
                case 17: return KEYS[114]; //star
                case 63: return KEYS[115]; //sym
                case 48: return KEYS[116]; //t
                case 61: return KEYS[117]; //tab
                case 49: return KEYS[118]; //u
                case 0: return KEYS[119]; //unknown
                case 50: return KEYS[120]; //v
                case 25: return KEYS[121]; //volumeDown
                case 24: return KEYS[122]; //volumeUp
                case 51: return KEYS[123]; //w
                case 52: return KEYS[124]; //x
                case 53: return KEYS[125]; //y
                case 54: return KEYS[126]; //z
                case 64: return KEYS[130]; //metaShiftLeftOn
                case 128: return KEYS[132]; //metaShiftRightOn
                case 129: return KEYS[134]; //controlLeft
                case 130: return KEYS[135]; //controlRight
                case 111: return KEYS[136]; //escape
                case 123: return KEYS[137]; //end
                case 124: return KEYS[138]; //insert
                case 92: return KEYS[139]; //pageUp
                case 93: return KEYS[140]; //pageDown
                case 94: return KEYS[141]; //pictSymbols
                case 95: return KEYS[142]; //switchCharset
                case 255: return KEYS[146]; //buttonC
                case 96: return KEYS[144]; //buttonA
                case 97: return KEYS[145]; //buttonB
                case 98: return KEYS[146]; //buttonC
                case 99: return KEYS[147]; //buttonX
                case 100: return KEYS[148]; //buttonY
                case 101: return KEYS[149]; //buttonZ
                case 102: return KEYS[150]; //buttonL1
                case 103: return KEYS[150]; //buttonL1
                case 104: return KEYS[152]; //buttonL2
                case 105: return KEYS[152]; //buttonL2
                case 106: return KEYS[154]; //buttonThumbL
                case 107: return KEYS[155]; //buttonThumbR
                case 108: return KEYS[156]; //buttonStart
                case 109: return KEYS[157]; //buttonSelect
                case 110: return KEYS[158]; //buttonMode
                case 144: return KEYS[159]; //numpad0
                case 145: return KEYS[160]; //numpad1
                case 146: return KEYS[161]; //numpad2
                case 147: return KEYS[162]; //numpad3
                case 148: return KEYS[163]; //numpad4
                case 149: return KEYS[164]; //numpad5
                case 150: return KEYS[165]; //numpad6
                case 151: return KEYS[166]; //numpad7
                case 152: return KEYS[167]; //numpad8
                case 153: return KEYS[168]; //numpad9
                case 243: return KEYS[169]; //colon
                case 131: return KEYS[170]; //f1
                case 132: return KEYS[171]; //f2
                case 133: return KEYS[172]; //f3
                case 134: return KEYS[173]; //f4
                case 135: return KEYS[174]; //f5
                case 136: return KEYS[175]; //f6
                case 137: return KEYS[176]; //f7
                case 138: return KEYS[177]; //f8
                case 139: return KEYS[178]; //f9
                case 140: return KEYS[179]; //f10
                case 141: return KEYS[180]; //f11
                case 142: return KEYS[181]; //f12
                default: return KEYS[119]; //unknown
            }
        }
    }
}