package copper.bridge.v2;

import io.anuke.arc.*;
import io.anuke.arc.input.*;
import copper.bridge.jvm.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;
import java.util.*;

/** The game's input, fed from the packed batches ART produces; polled and read on the game loop thread alone. */
public class BridgeInput extends Input {
    private static final int MAX_TOUCHES = 20;

    private static final Peripheral ONSCREEN_KEYBOARD = Peripheral.OnscreenKeyboard;
    private static final Peripheral MULTITOUCH_SCREEN = Peripheral.MultitouchScreen;
    private static final Peripheral VIBRATOR = Peripheral.Vibrator;
    private static final Peripheral PRESSURE = Peripheral.Pressure;
    private static final Orientation LANDSCAPE = Orientation.Landscape;
    private static final Orientation PORTRAIT = Orientation.Portrait;

    /** Absent at the range's floor; looked up once so the frame loop does not pay for a failed lookup. */
    private static final java.lang.reflect.Method POST_UPDATE = postUpdateMethod();

    private static java.lang.reflect.Method postUpdateMethod() {
        try {
            return InputDevice.class.getMethod("postUpdate");
        } catch (Throwable ignored) {
            return null;
        }
    }


    /** The schema this bridge binds; an inner class because this one already extends arc's {@code Input}. */
    public final class Events extends InputBatch {
        @Override
        public void pointerDown(int pointerId, float rawX, float rawY, float rawPressure) {
            // A down for an already tracked pointer keeps its slot: a second one would strand the first.
            int index = lookUpPointerIndex(pointerId);
            if (index == -1) index = freePointerIndex();
            if (index == -1) return;

            int x = (int)rawX;
            int y = Core.graphics.getHeight() - 1 - (int)rawY;

            realId[index] = pointerId;
            touchX[index] = x;
            touchY[index] = y;
            deltaX[index] = 0;
            deltaY[index] = 0;
            button[index] = KeyCode.MOUSE_LEFT.ordinal();
            pressure[index] = rawPressure;
            touched[index] = true;

            inputMultiplexer.touchDown(x, y, index, KeyCode.MOUSE_LEFT);
            justTouched = true;
        }

        @Override
        public void pointerUp(int pointerId, float rawX, float rawY) {
            int index = lookUpPointerIndex(pointerId);
            if (index == -1) return;

            int x = (int)rawX;
            int y = Core.graphics.getHeight() - 1 - (int)rawY;
            KeyCode key = KeyCode.byOrdinal(button[index]);

            realId[index] = -1;
            touchX[index] = x;
            touchY[index] = y;
            deltaX[index] = 0;
            deltaY[index] = 0;
            button[index] = 0;
            pressure[index] = 0f;
            touched[index] = false;

            if (key != KeyCode.UNKNOWN) {
                inputMultiplexer.touchUp(x, y, index, key);
            }
        }

        @Override
        public void pointerMove(int pointerId, float rawX, float rawY, float rawPressure) {
            int index = lookUpPointerIndex(pointerId);
            int x = (int)rawX;
            int y = Core.graphics.getHeight() - 1 - (int)rawY;

            if (index == -1) {
                if (x == mouseLastX && (int)rawY == mouseLastY) return;

                inputMultiplexer.mouseMoved(x, y);
                deltaX[0] = x - mouseLastX;
                deltaY[0] = -((int)rawY - mouseLastY);
                touchX[0] = x;
                touchY[0] = y;
                mouseLastX = x;
                mouseLastY = (int)rawY;
                return;
            }

            KeyCode key = KeyCode.byOrdinal(button[index]);

            // arc's own Android computation: the game must read the same values as on vanilla Android.
            deltaX[index] = x - touchX[index];
            deltaY[index] = -((int)rawY - touchY[index]);
            touchX[index] = x;
            touchY[index] = y;
            pressure[index] = rawPressure;

            if (key != KeyCode.UNKNOWN) {
                inputMultiplexer.touchDragged(x, y, index);
            } else {
                inputMultiplexer.mouseMoved(x, y);
            }
        }

        @Override
        public void pointerCancel() {
            // no synthetic release: the game's tap handling would turn a cancel into a click where the finger was
            clearPointers();
        }

        @Override
        public void scroll(float scrollY) {
            inputMultiplexer.scrolled(0f, scrollY);
        }

        /** A repeat above zero and a down for an already-down key are consumed; the multiplexer delivers it. */
        @Override
        public void keyDown(int deviceCode, int repeatCount) {
            if (repeatCount > 0) return;

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

    private final int[] touchX = new int[MAX_TOUCHES];
    private final int[] touchY = new int[MAX_TOUCHES];
    private final int[] deltaX = new int[MAX_TOUCHES];
    private final int[] deltaY = new int[MAX_TOUCHES];
    private final int[] button = new int[MAX_TOUCHES];
    private final float[] pressure = new float[MAX_TOUCHES];
    private final boolean[] touched = new boolean[MAX_TOUCHES];
    /** The Android pointer id a slot is tracking, or -1 when the slot is free. */
    private final int[] realId = new int[MAX_TOUCHES];

    /** The previous hover position, which is only ever set by a mouse or a stylus, never by a touch. */
    private int mouseLastX;
    private int mouseLastY;

    private final boolean[] downState = new boolean[KeyCode.all.length];

    private boolean justTouched;
    private long currentEventTimeStamp = System.nanoTime();

    public BridgeInput() {
        Arrays.fill(realId, -1);
        JvmBatch.bind(new Events());
    }

    /** One back tap through the normal input path, used when no dialog is open. */
    public void backTap() {
        inputMultiplexer.keyDown(KeyCode.BACK);
        inputMultiplexer.keyUp(KeyCode.BACK);
    }

    /** Drains every pending frame and replays it, so a swipe sampled faster than the frame rate arrives whole. */
    public void processEvents() {
        justTouched = false;

        JvmBatch.input.poll();
        while (JvmBatch.input.hasNext()) {
            JvmBatch.input.processHeader();

            currentEventTimeStamp = System.nanoTime();

            JvmBatch.input.processRecords();
            JvmBatch.input.next();
        }
    }

    /** Not a loop over {@code Input.devices}: arc removes that field in v152, and the keyboard is its only device. */
    public void processDevices() {
        if (POST_UPDATE == null)
            return;
        try {
            POST_UPDATE.invoke(keyboard);
        } catch (Throwable ignored) {
        }
    }

    public void onPause() {
        clearState();
    }

    public void onResume() {
        clearState();
    }

    private void clearState() {
        Arrays.fill(downState, false);
        clearPointers();
    }

    private void clearPointers() {
        for (int i = 0; i < MAX_TOUCHES; i++) {
            realId[i] = -1;
            touchX[i] = 0;
            touchY[i] = 0;
            deltaX[i] = 0;
            deltaY[i] = 0;
            button[i] = 0;
            pressure[i] = 0f;
            touched[i] = false;
        }
        mouseLastX = 0;
        mouseLastY = 0;
    }

    //region pointers

    private int freePointerIndex() {
        for (int i = 0; i < MAX_TOUCHES; i++) {
            if (realId[i] == -1) return i;
        }
        return -1;
    }

    private int lookUpPointerIndex(int pointerId) {
        for (int i = 0; i < MAX_TOUCHES; i++) {
            if (realId[i] == pointerId) return i;
        }
        return -1;
    }
    //endregion

    //region text input and other peripherals

    /** {@code TextInput.numeric} was added after 4550; the older dialog has no numeric mode to ask for. */
    private static boolean numeric(TextInput info) {
        try {
            return (boolean)TextInput.class.getField("numeric").get(info);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Shows the text input dialog ART owns; the answer arrives on the game loop. */
    @Override
    public void getTextInput(TextInput info) {
        // the 6 line's TextInput has no hint line and no empty-answer flag, so the dialog gets neither
        JvmCall.textInput(info.title, "", info.text, numeric(info), info.multiline,
                info.maxLength, false)
                .onResult(value -> info.accepted.accept(value))
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
        return touchX[0];
    }

    @Override
    public int mouseX(int pointer) {
        return touchX[pointer];
    }

    @Override
    public int mouseY() {
        return touchY[0];
    }

    @Override
    public int mouseY(int pointer) {
        return touchY[pointer];
    }

    @Override
    public int deltaX() {
        return deltaX[0];
    }

    @Override
    public int deltaX(int pointer) {
        return deltaX[pointer];
    }

    @Override
    public int deltaY() {
        return deltaY[0];
    }

    @Override
    public int deltaY(int pointer) {
        return deltaY[pointer];
    }

    @Override
    public boolean isTouched() {
        for (int pointer = 0; pointer < MAX_TOUCHES; pointer++) {
            if (touched[pointer]) return true;
        }
        return false;
    }

    @Override
    public boolean isTouched(int pointer) {
        return touched[pointer];
    }

    @Override
    public boolean justTouched() {
        return justTouched;
    }

    @Override
    public float getPressure(int pointer) {
        return pressure[pointer];
    }

    @Override
    public long getCurrentEventTime() {
        return currentEventTimeStamp;
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