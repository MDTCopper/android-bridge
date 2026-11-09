package copper.bridge.v4;

import io.anuke.arc.*;
import io.anuke.arc.input.*;
import copper.bridge.backend.input.InputEngine;
import copper.bridge.jvm.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;
import copper.bridge.util.WireCharBuffer;
import java.util.*;

/** The game's input, fed from the packed batches ART produces; polled and read on the game loop thread alone. */
public class BridgeInput extends Input implements copper.bridge.v1.InputHost {
    private static final Peripheral ONSCREEN_KEYBOARD = Peripheral.OnscreenKeyboard;
    private static final Peripheral MULTITOUCH_SCREEN = Peripheral.MultitouchScreen;
    private static final Peripheral VIBRATOR = Peripheral.Vibrator;
    private static final Peripheral PRESSURE = Peripheral.Pressure;
    private static final Orientation LANDSCAPE = Orientation.Landscape;
    private static final Orientation PORTRAIT = Orientation.Portrait;

    private final InputEngine engine;
    private final boolean[] downState = new boolean[KeyCode.all.length];

    public BridgeInput() {
        engine = new InputEngine(new Sink(), () -> Core.graphics.getHeight());
    }

    /** Maps the backend's raw pointer/key events to arc's input multiplexer. */
    private final class Sink implements InputEngine.Sink {
        @Override
        public void touchDown(int pointer, int x, int y) {
            inputMultiplexer.touchDown(x, y, pointer, KeyCode.MOUSE_LEFT);
        }

        @Override
        public void touchUp(int pointer, int x, int y) {
            inputMultiplexer.touchUp(x, y, pointer, KeyCode.MOUSE_LEFT);
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
        inputMultiplexer.keyDown(KeyCode.BACK);
        inputMultiplexer.keyUp(KeyCode.BACK);
    }

    /** Drains every pending frame and replays it, so a swipe sampled faster than the frame rate arrives whole. */
    public void processEvents() {
        engine.processEvents();
    }

    /** Absent in this range; the input devices have no per-frame update pass. */
    public void processDevices() {
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
                case -1: return KeyCode.ANY_KEY;
                case 7: return KeyCode.NUM_0;
                case 8: return KeyCode.NUM_1;
                case 9: return KeyCode.NUM_2;
                case 10: return KeyCode.NUM_3;
                case 11: return KeyCode.NUM_4;
                case 12: return KeyCode.NUM_5;
                case 13: return KeyCode.NUM_6;
                case 14: return KeyCode.NUM_7;
                case 15: return KeyCode.NUM_8;
                case 16: return KeyCode.NUM_9;
                case 29: return KeyCode.A;
                case 57: return KeyCode.ALT_LEFT;
                case 58: return KeyCode.ALT_RIGHT;
                case 75: return KeyCode.APOSTROPHE;
                case 77: return KeyCode.AT;
                case 30: return KeyCode.B;
                case 4: return KeyCode.BACK;
                case 73: return KeyCode.BACKSLASH;
                case 31: return KeyCode.C;
                case 5: return KeyCode.CALL;
                case 27: return KeyCode.CAMERA;
                case 28: return KeyCode.CLEAR;
                case 55: return KeyCode.COMMA;
                case 32: return KeyCode.D;
                case 67: return KeyCode.BACKSPACE;
                case 112: return KeyCode.FORWARD_DEL;
                case 23: return KeyCode.CENTER;
                case 20: return KeyCode.DOWN;
                case 21: return KeyCode.LEFT;
                case 22: return KeyCode.RIGHT;
                case 19: return KeyCode.UP;
                case 33: return KeyCode.E;
                case 6: return KeyCode.ENDCALL;
                case 66: return KeyCode.ENTER;
                case 65: return KeyCode.ENVELOPE;
                case 70: return KeyCode.EQUALS;
                case 34: return KeyCode.F;
                case 80: return KeyCode.FOCUS;
                case 35: return KeyCode.G;
                case 68: return KeyCode.BACKTICK;
                case 36: return KeyCode.H;
                case 79: return KeyCode.HEADSETHOOK;
                case 3: return KeyCode.HOME;
                case 37: return KeyCode.I;
                case 38: return KeyCode.J;
                case 39: return KeyCode.K;
                case 40: return KeyCode.L;
                case 71: return KeyCode.LEFT_BRACKET;
                case 41: return KeyCode.M;
                case 90: return KeyCode.MEDIA_FAST_FORWARD;
                case 87: return KeyCode.MEDIA_NEXT;
                case 85: return KeyCode.MEDIA_PLAY_PAUSE;
                case 88: return KeyCode.MEDIA_PREVIOUS;
                case 89: return KeyCode.MEDIA_REWIND;
                case 86: return KeyCode.MEDIA_STOP;
                case 82: return KeyCode.MENU;
                case 69: return KeyCode.MINUS;
                case 91: return KeyCode.MUTE;
                case 42: return KeyCode.N;
                case 83: return KeyCode.NOTIFICATION;
                case 78: return KeyCode.NUM;
                case 43: return KeyCode.O;
                case 44: return KeyCode.P;
                case 56: return KeyCode.PERIOD;
                case 81: return KeyCode.PLUS;
                case 18: return KeyCode.POUND;
                case 26: return KeyCode.POWER;
                case 45: return KeyCode.Q;
                case 46: return KeyCode.R;
                case 72: return KeyCode.RIGHT_BRACKET;
                case 47: return KeyCode.S;
                case 84: return KeyCode.SEARCH;
                case 74: return KeyCode.SEMICOLON;
                case 59: return KeyCode.SHIFT_LEFT;
                case 60: return KeyCode.SHIFT_RIGHT;
                case 76: return KeyCode.SLASH;
                case 1: return KeyCode.SOFT_LEFT;
                case 2: return KeyCode.SOFT_RIGHT;
                case 62: return KeyCode.SPACE;
                case 17: return KeyCode.STAR;
                case 63: return KeyCode.SYM;
                case 48: return KeyCode.T;
                case 61: return KeyCode.TAB;
                case 49: return KeyCode.U;
                case 0: return KeyCode.UNKNOWN;
                case 50: return KeyCode.V;
                case 25: return KeyCode.VOLUME_DOWN;
                case 24: return KeyCode.VOLUME_UP;
                case 51: return KeyCode.W;
                case 52: return KeyCode.X;
                case 53: return KeyCode.Y;
                case 54: return KeyCode.Z;
                case 64: return KeyCode.META_SHIFT_LEFT_ON;
                case 128: return KeyCode.META_SHIFT_RIGHT_ON;
                case 129: return KeyCode.CONTROL_LEFT;
                case 130: return KeyCode.CONTROL_RIGHT;
                case 111: return KeyCode.ESCAPE;
                case 123: return KeyCode.END;
                case 124: return KeyCode.INSERT;
                case 92: return KeyCode.PAGE_UP;
                case 93: return KeyCode.PAGE_DOWN;
                case 94: return KeyCode.PICTSYMBOLS;
                case 95: return KeyCode.SWITCH_CHARSET;
                case 255: return KeyCode.BUTTON_C;
                case 96: return KeyCode.BUTTON_A;
                case 97: return KeyCode.BUTTON_B;
                case 98: return KeyCode.BUTTON_C;
                case 99: return KeyCode.BUTTON_X;
                case 100: return KeyCode.BUTTON_Y;
                case 101: return KeyCode.BUTTON_Z;
                case 102: return KeyCode.BUTTON_L1;
                case 103: return KeyCode.BUTTON_L1;
                case 104: return KeyCode.BUTTON_L2;
                case 105: return KeyCode.BUTTON_L2;
                case 106: return KeyCode.BUTTON_THUMBL;
                case 107: return KeyCode.BUTTON_THUMBR;
                case 108: return KeyCode.BUTTON_START;
                case 109: return KeyCode.BUTTON_SELECT;
                case 110: return KeyCode.BUTTON_MODE;
                case 144: return KeyCode.NUMPAD_0;
                case 145: return KeyCode.NUMPAD_1;
                case 146: return KeyCode.NUMPAD_2;
                case 147: return KeyCode.NUMPAD_3;
                case 148: return KeyCode.NUMPAD_4;
                case 149: return KeyCode.NUMPAD_5;
                case 150: return KeyCode.NUMPAD_6;
                case 151: return KeyCode.NUMPAD_7;
                case 152: return KeyCode.NUMPAD_8;
                case 153: return KeyCode.NUMPAD_9;
                case 243: return KeyCode.COLON;
                case 131: return KeyCode.F1;
                case 132: return KeyCode.F2;
                case 133: return KeyCode.F3;
                case 134: return KeyCode.F4;
                case 135: return KeyCode.F5;
                case 136: return KeyCode.F6;
                case 137: return KeyCode.F7;
                case 138: return KeyCode.F8;
                case 139: return KeyCode.F9;
                case 140: return KeyCode.F10;
                case 141: return KeyCode.F11;
                case 142: return KeyCode.F12;
                default: return KeyCode.UNKNOWN;
            }
        }
    }
}