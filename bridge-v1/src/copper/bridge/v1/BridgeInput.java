package copper.bridge.v1;

import arc.*;
import arc.input.*;
import copper.bridge.jvm.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;
import java.util.*;

/** The game's input, fed from the packed batches ART produces; polled and read on the game loop thread alone. */
public class BridgeInput extends Input {
    private static final int MAX_TOUCHES = 20;

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
            button[index] = KeyCode.mouseLeft.ordinal();
            pressure[index] = rawPressure;
            touched[index] = true;

            inputMultiplexer.touchDown(x, y, index, KeyCode.mouseLeft);
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

            if (key != KeyCode.unknown) {
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

            if (key != KeyCode.unknown) {
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
        inputMultiplexer.keyDown(arc.input.KeyCode.back);
        inputMultiplexer.keyUp(arc.input.KeyCode.back);
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
        keyboard.postUpdate();
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

    /** Shows the text input dialog ART owns; the answer arrives on the game loop. */
    @Override
    public void getTextInput(TextInput info) {
        JvmCall.textInput(info.title, info.message, info.text, info.numeric, info.multiline,
                info.maxLength, info.allowEmpty)
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
        if (peripheral == Peripheral.onscreenKeyboard) return true;
        if (peripheral == Peripheral.multitouchScreen) return true;
        if (peripheral == Peripheral.vibrator) return JvmCall.isVibratorAvailable();
        return peripheral == Peripheral.pressure;
    }

    /** The display's rotation in degrees, translated on the ART side, which owns the constants. */
    @Override
    public int getRotation() {
        return JvmCall.screenRotationDegrees();
    }

    /** Whether the device's display is natively landscape, asked of ART and not cached. */
    @Override
    public Orientation getNativeOrientation() {
        return JvmCall.isNativeLandscape() ? Orientation.landscape : Orientation.portrait;
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
                case -1: return KeyCode.anyKey;
                case 7: return KeyCode.num0;
                case 8: return KeyCode.num1;
                case 9: return KeyCode.num2;
                case 10: return KeyCode.num3;
                case 11: return KeyCode.num4;
                case 12: return KeyCode.num5;
                case 13: return KeyCode.num6;
                case 14: return KeyCode.num7;
                case 15: return KeyCode.num8;
                case 16: return KeyCode.num9;
                case 29: return KeyCode.a;
                case 57: return KeyCode.altLeft;
                case 58: return KeyCode.altRight;
                case 75: return KeyCode.apostrophe;
                case 77: return KeyCode.at;
                case 30: return KeyCode.b;
                case 4: return KeyCode.back;
                case 73: return KeyCode.backslash;
                case 31: return KeyCode.c;
                case 5: return KeyCode.call;
                case 27: return KeyCode.camera;
                case 28: return KeyCode.clear;
                case 55: return KeyCode.comma;
                case 32: return KeyCode.d;
                case 67: return KeyCode.backspace;
                case 112: return KeyCode.forwardDel;
                case 23: return KeyCode.center;
                case 20: return KeyCode.down;
                case 21: return KeyCode.left;
                case 22: return KeyCode.right;
                case 19: return KeyCode.up;
                case 33: return KeyCode.e;
                case 6: return KeyCode.endcall;
                case 66: return KeyCode.enter;
                case 65: return KeyCode.envelope;
                case 70: return KeyCode.equals;
                case 34: return KeyCode.f;
                case 80: return KeyCode.focus;
                case 35: return KeyCode.g;
                case 68: return KeyCode.backtick;
                case 36: return KeyCode.h;
                case 79: return KeyCode.headsetHook;
                case 3: return KeyCode.home;
                case 37: return KeyCode.i;
                case 38: return KeyCode.j;
                case 39: return KeyCode.k;
                case 40: return KeyCode.l;
                case 71: return KeyCode.leftBracket;
                case 41: return KeyCode.m;
                case 90: return KeyCode.mediaFastForward;
                case 87: return KeyCode.mediaNext;
                case 85: return KeyCode.mediaPlayPause;
                case 88: return KeyCode.mediaPrevious;
                case 89: return KeyCode.mediaRewind;
                case 86: return KeyCode.mediaStop;
                case 82: return KeyCode.menu;
                case 69: return KeyCode.minus;
                case 91: return KeyCode.mute;
                case 42: return KeyCode.n;
                case 83: return KeyCode.notification;
                case 78: return KeyCode.num;
                case 43: return KeyCode.o;
                case 44: return KeyCode.p;
                case 56: return KeyCode.period;
                case 81: return KeyCode.plus;
                case 18: return KeyCode.pound;
                case 26: return KeyCode.power;
                case 45: return KeyCode.q;
                case 46: return KeyCode.r;
                case 72: return KeyCode.rightBracket;
                case 47: return KeyCode.s;
                case 84: return KeyCode.search;
                case 74: return KeyCode.semicolon;
                case 59: return KeyCode.shiftLeft;
                case 60: return KeyCode.shiftRight;
                case 76: return KeyCode.slash;
                case 1: return KeyCode.softLeft;
                case 2: return KeyCode.softRight;
                case 62: return KeyCode.space;
                case 17: return KeyCode.star;
                case 63: return KeyCode.sym;
                case 48: return KeyCode.t;
                case 61: return KeyCode.tab;
                case 49: return KeyCode.u;
                case 0: return KeyCode.unknown;
                case 50: return KeyCode.v;
                case 25: return KeyCode.volumeDown;
                case 24: return KeyCode.volumeUp;
                case 51: return KeyCode.w;
                case 52: return KeyCode.x;
                case 53: return KeyCode.y;
                case 54: return KeyCode.z;
                case 64: return KeyCode.metaShiftLeftOn;
                case 128: return KeyCode.metaShiftRightOn;
                case 129: return KeyCode.controlLeft;
                case 130: return KeyCode.controlRight;
                case 111: return KeyCode.escape;
                case 123: return KeyCode.end;
                case 124: return KeyCode.insert;
                case 92: return KeyCode.pageUp;
                case 93: return KeyCode.pageDown;
                case 94: return KeyCode.pictSymbols;
                case 95: return KeyCode.switchCharset;
                case 255: return KeyCode.buttonC;
                case 96: return KeyCode.buttonA;
                case 97: return KeyCode.buttonB;
                case 98: return KeyCode.buttonC;
                case 99: return KeyCode.buttonX;
                case 100: return KeyCode.buttonY;
                case 101: return KeyCode.buttonZ;
                case 102: return KeyCode.buttonL1;
                case 103: return KeyCode.buttonL1;
                case 104: return KeyCode.buttonL2;
                case 105: return KeyCode.buttonL2;
                case 106: return KeyCode.buttonThumbL;
                case 107: return KeyCode.buttonThumbR;
                case 108: return KeyCode.buttonStart;
                case 109: return KeyCode.buttonSelect;
                case 110: return KeyCode.buttonMode;
                case 144: return KeyCode.numpad0;
                case 145: return KeyCode.numpad1;
                case 146: return KeyCode.numpad2;
                case 147: return KeyCode.numpad3;
                case 148: return KeyCode.numpad4;
                case 149: return KeyCode.numpad5;
                case 150: return KeyCode.numpad6;
                case 151: return KeyCode.numpad7;
                case 152: return KeyCode.numpad8;
                case 153: return KeyCode.numpad9;
                case 243: return KeyCode.colon;
                case 131: return KeyCode.f1;
                case 132: return KeyCode.f2;
                case 133: return KeyCode.f3;
                case 134: return KeyCode.f4;
                case 135: return KeyCode.f5;
                case 136: return KeyCode.f6;
                case 137: return KeyCode.f7;
                case 138: return KeyCode.f8;
                case 139: return KeyCode.f9;
                case 140: return KeyCode.f10;
                case 141: return KeyCode.f11;
                case 142: return KeyCode.f12;
                default: return KeyCode.unknown;
            }
        }
    }
}
