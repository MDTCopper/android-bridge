package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.gen.JvmCall;
import copper.bridge.jvm.InputBatch;
import copper.bridge.gen.JvmBatch;
import copper.bridge.util.Log;
import copper.bridge.util.WireCharBuffer;

import com.badlogic.gdx.*;



import java.util.*;

/**
 * libGDX input, fed from the packed batches ART produces; polled and read on the game loop thread alone. Pointer
 * slots are libGDX pointer indices, while the Android id is kept to find the same finger on a later move or up.
 */
public class BridgeInput implements Input {
    private static final int MAX_TOUCHES = 20;

    /** The schema this bridge binds; an inner class because this one already implements libGDX's {@code Input}. */
    public final class Events extends InputBatch {
        @Override
        public void pointerDown(int pointerId, float rawX, float rawY, float rawPressure) {
            int index = lookUpPointerIndex(pointerId);
            if (index == -1) index = freePointerIndex();
            if (index == -1) return;

            int x = (int)rawX;
            int y = (int)rawY;

            realId[index] = pointerId;
            touchX[index] = x;
            touchY[index] = y;
            deltaX[index] = 0;
            deltaY[index] = 0;
            pressure[index] = rawPressure;
            touched[index] = true;

            if (processor != null)
                processor.touchDown(x, y, index, Buttons.LEFT);
            justTouched = true;
        }

        @Override
        public void pointerUp(int pointerId, float rawX, float rawY) {
            int index = lookUpPointerIndex(pointerId);
            if (index == -1) return;

            int x = (int)rawX;
            int y = (int)rawY;

            touchX[index] = x;
            touchY[index] = y;

            if (processor != null)
                processor.touchUp(x, y, index, Buttons.LEFT);

            realId[index] = -1;
            deltaX[index] = 0;
            deltaY[index] = 0;
            pressure[index] = 0f;
            touched[index] = false;
        }

        @Override
        public void pointerMove(int pointerId, float rawX, float rawY, float rawPressure) {
            int index = lookUpPointerIndex(pointerId);
            int x = (int)rawX;
            int y = (int)rawY;

            if (index == -1) {
                if (x == mouseLastX && (int)rawY == mouseLastY) return;

                if (processor != null)
                    processor.mouseMoved(x, y);
                deltaX[0] = x - mouseLastX;
                deltaY[0] = (int)rawY - mouseLastY;
                touchX[0] = x;
                touchY[0] = y;
                mouseLastX = x;
                mouseLastY = (int)rawY;
                return;
            }

            deltaX[index] = x - touchX[index];
            deltaY[index] = (int)rawY - touchY[index];
            touchX[index] = x;
            touchY[index] = y;
            pressure[index] = rawPressure;

            if (processor != null)
                processor.touchDragged(x, y, index);
        }

        @Override
        public void pointerCancel() {
            clearPointers();
        }

        @Override
        public void scroll(float scrollY) {
            if (processor != null)
                processor.scrolled((int)scrollY);
        }

        @Override
        public void keyDown(int deviceCode, int repeatCount) {
            if (repeatCount > 0) return;

            int key = translate(deviceCode);
            if (key == Keys.UNKNOWN || downState[key]) return;

            downState[key] = true;
            justPressed[key] = true;
            if (processor != null)
                processor.keyDown(key);
        }

        @Override
        public void keyUp(int deviceCode, WireCharBuffer chars) {
            int key = translate(deviceCode);
            if (key == Keys.UNKNOWN || !downState[key]) return;

            downState[key] = false;
            if (processor != null)
                processor.keyUp(key);
            type(chars);
        }

        @Override
        public void text(WireCharBuffer chars) {
            type(chars);
        }

        private void type(WireCharBuffer chars) {
            if (processor == null) return;
            for (int i = 0; i < chars.length(); i++)
                processor.keyTyped(chars.charAt(i));
        }
    }

    private final int[] touchX = new int[MAX_TOUCHES];
    private final int[] touchY = new int[MAX_TOUCHES];
    private final int[] deltaX = new int[MAX_TOUCHES];
    private final int[] deltaY = new int[MAX_TOUCHES];
    private final float[] pressure = new float[MAX_TOUCHES];
    private final boolean[] touched = new boolean[MAX_TOUCHES];
    /** The Android pointer id a slot is tracking, or -1 when the slot is free. */
    private final int[] realId = new int[MAX_TOUCHES];

    /** The previous hover position, which is only ever set by a mouse or a stylus, never by a touch. */
    private int mouseLastX;
    private int mouseLastY;

    private final boolean[] downState = new boolean[256];
    private final boolean[] justPressed = new boolean[256];
    private boolean justTouched;
    private long currentEventTimeStamp = System.nanoTime();
    private InputProcessor processor;

    public BridgeInput() {
        Arrays.fill(realId, -1);
        JvmBatch.bind(new Events());
    }

    /** One back tap through the normal input path; false when nothing consumed it. */
    public boolean backTap() {
        boolean handled = processor != null && processor.keyDown(Keys.BACK);
        if (processor != null)
            processor.keyUp(Keys.BACK);
        return handled;
    }

    /** Drains every pending frame and replays it, so a swipe sampled faster than the frame rate arrives whole. */
    public void processEvents() {
        justTouched = false;
        Arrays.fill(justPressed, false);

        JvmBatch.input.poll();
        while (JvmBatch.input.hasNext()) {
            JvmBatch.input.processHeader();
            currentEventTimeStamp = System.nanoTime();
            JvmBatch.input.processRecords();
            JvmBatch.input.next();
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
        Arrays.fill(justPressed, false);
        clearPointers();
    }

    private void clearPointers() {
        for (int i = 0; i < MAX_TOUCHES; i++) {
            realId[i] = -1;
            touchX[i] = 0;
            touchY[i] = 0;
            deltaX[i] = 0;
            deltaY[i] = 0;
            pressure[i] = 0f;
            touched[i] = false;
        }
        mouseLastX = 0;
        mouseLastY = 0;
    }

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

    /**
     * Android key codes are libGDX's except where libGDX renumbered the dedicated keys; the game sees the
     * libGDX value because that is what its key bindings are compiled against.
     */
    private static int translate(int key) {
        switch (key) {
            case 111: return Keys.ESCAPE;
            case 123: return Keys.END;
            case 124: return Keys.INSERT;
            case 131: return Keys.F1;
            case 132: return Keys.F2;
            case 133: return Keys.F3;
            case 134: return Keys.F4;
            case 135: return Keys.F5;
            case 136: return Keys.F6;
            case 137: return Keys.F7;
            case 138: return Keys.F8;
            case 139: return Keys.F9;
            case 140: return Keys.F10;
            case 141: return Keys.F11;
            case 142: return Keys.F12;
            default: return key < 0 || key > 255 ? Keys.UNKNOWN : key;
        }
    }

    @Override
    @BridgeOverride
    public float getAccelerometerX() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getAccelerometerY() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getAccelerometerZ() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getGyroscopeX() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getGyroscopeY() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getGyroscopeZ() {
        return 0;
    }

    @Override
    @BridgeOverride
    public int getX() {
        return touchX[0];
    }

    @Override
    @BridgeOverride
    public int getX(int pointer) {
        return pointer < MAX_TOUCHES ? touchX[pointer] : 0;
    }

    @Override
    @BridgeOverride
    public int getDeltaX() {
        return deltaX[0];
    }

    @Override
    @BridgeOverride
    public int getDeltaX(int pointer) {
        return pointer < MAX_TOUCHES ? deltaX[pointer] : 0;
    }

    @Override
    @BridgeOverride
    public int getY() {
        return touchY[0];
    }

    @Override
    @BridgeOverride
    public int getY(int pointer) {
        return pointer < MAX_TOUCHES ? touchY[pointer] : 0;
    }

    @Override
    @BridgeOverride
    public int getDeltaY() {
        return deltaY[0];
    }

    @Override
    @BridgeOverride
    public int getDeltaY(int pointer) {
        return pointer < MAX_TOUCHES ? deltaY[pointer] : 0;
    }

    @Override
    @BridgeOverride
    public boolean isTouched() {
        for (boolean touch : touched) {
            if (touch) return true;
        }
        return false;
    }

    @Override
    @BridgeOverride
    public boolean justTouched() {
        return justTouched;
    }

    @Override
    @BridgeOverride
    public boolean isTouched(int pointer) {
        return pointer < MAX_TOUCHES && touched[pointer];
    }

    @Override
    @BridgeOverride
    public boolean isButtonPressed(int button) {
        return false;
    }

    @Override
    @BridgeOverride
    public boolean isKeyPressed(int key) {
        return key >= 0 && key < 256 && downState[key];
    }

    @Override
    @BridgeOverride
    public boolean isKeyJustPressed(int key) {
        return key >= 0 && key < 256 && justPressed[key];
    }

    @Override
    @BridgeOverride
    public void getTextInput(TextInputListener listener, String title, String text, String hint) {
        JvmCall.textInput(title, hint == null ? "" : hint, text == null ? "" : text, false, false, 100, true)
                .onResult(listener::input)
                .onCanceled(listener::canceled);
    }

    @Override
    @BridgeOverride
    public void setOnscreenKeyboardVisible(boolean visible) {
        JvmCall.onscreenKeyboard(visible);
    }

    @Override
    @BridgeOverride
    public void vibrate(int milliseconds) {
        JvmCall.vibrate(milliseconds);
    }

    @Override
    @BridgeOverride
    public void vibrate(long[] pattern, int repeat) {
        JvmCall.vibrate(pattern, repeat);
    }

    @Override
    @BridgeOverride
    public void cancelVibrate() {
        JvmCall.cancelVibrate();
    }

    @Override
    @BridgeOverride
    public float getAzimuth() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getPitch() {
        return 0;
    }

    @Override
    @BridgeOverride
    public float getRoll() {
        return 0;
    }

    @Override
    @BridgeOverride
    public void getRotationMatrix(float[] matrix) {
    }

    @Override
    @BridgeOverride
    public long getCurrentEventTime() {
        return currentEventTimeStamp;
    }

    @Override
    @BridgeOverride
    public void setCatchBackKey(boolean catchBack) {
    }

    @Override
    @BridgeOverride
    public boolean isCatchBackKey() {
        return false;
    }

    @Override
    @BridgeOverride
    public void setCatchMenuKey(boolean catchMenu) {
    }

    @Override
    @BridgeOverride
    public boolean isCatchMenuKey() {
        return false;
    }

    @Override
    @BridgeOverride
    public void setInputProcessor(InputProcessor processor) {
        this.processor = processor;
    }

    @Override
    @BridgeOverride
    public InputProcessor getInputProcessor() {
        return processor;
    }

    @Override
    @BridgeOverride
    public boolean isPeripheralAvailable(Peripheral peripheral) {
        if (peripheral == Peripheral.OnscreenKeyboard || peripheral == Peripheral.MultitouchScreen)
            return true;
        if (peripheral == Peripheral.Vibrator)
            return JvmCall.isVibratorAvailable();
        return false;
    }

    @Override
    @BridgeOverride
    public int getRotation() {
        return JvmCall.screenRotationDegrees();
    }

    @Override
    @BridgeOverride
    public Orientation getNativeOrientation() {
        return JvmCall.isNativeLandscape() ? Orientation.Landscape : Orientation.Portrait;
    }

    @Override
    @BridgeOverride
    public void setCursorCatched(boolean catched) {
    }

    @Override
    @BridgeOverride
    public boolean isCursorCatched() {
        return false;
    }

    @Override
    @BridgeOverride
    public void setCursorPosition(int x, int y) {
    }
}
