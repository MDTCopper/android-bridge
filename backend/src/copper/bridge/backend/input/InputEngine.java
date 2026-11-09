package copper.bridge.backend.input;

import copper.bridge.jvm.InputBatch;
import copper.bridge.gen.*;
import copper.bridge.util.WireCharBuffer;

import java.util.Arrays;
import java.util.function.IntSupplier;

/**
 * The pointer and batch half of input: slotting Android pointer ids, keeping the per-slot positions and deltas, and
 * polling the packed frames. Key-code translation stays in the branch adapter because the enum differs by version.
 */
public final class InputEngine extends InputBatch {
    public static final int MAX_POINTERS = 20;

    /** Receives already-slotted pointer events; the adapter maps them to arc's input multiplexer. */
    public interface Sink {
        void touchDown(int pointer, int x, int y);

        void touchUp(int pointer, int x, int y);

        /** {@code pointer} is -1 for a hover move. */
        void pointerMove(int pointer, int x, int y);

        void scroll(float amount);

        void keyDown(int deviceCode, int repeat);

        void keyUp(int deviceCode, WireCharBuffer chars);

        void text(WireCharBuffer chars);
    }

    private final Sink sink;
    private final IntSupplier height;

    private final int[] touchX = new int[MAX_POINTERS];
    private final int[] touchY = new int[MAX_POINTERS];
    private final int[] deltaX = new int[MAX_POINTERS];
    private final int[] deltaY = new int[MAX_POINTERS];
    private final float[] pressure = new float[MAX_POINTERS];
    private final boolean[] touched = new boolean[MAX_POINTERS];
    /** The Android pointer id a slot is tracking, or -1 when the slot is free. */
    private final int[] realId = new int[MAX_POINTERS];

    /** The previous hover position, which is only ever set by a mouse or a stylus, never by a touch. */
    private int mouseLastX;
    private int mouseLastY;

    private boolean justTouched;
    private long currentEventTimeStamp = System.nanoTime();

    public InputEngine(Sink sink, IntSupplier height) {
        this.sink = sink;
        this.height = height;
        Arrays.fill(realId, -1);
        JvmBatch.bind(this);
    }

    @Override
    public void pointerDown(int pointerId, float rawX, float rawY, float rawPressure) {
        // A down for an already tracked pointer keeps its slot: a second one would strand the first.
        int index = lookUpPointerIndex(pointerId);
        if (index == -1) index = freePointerIndex();
        if (index == -1) return;

        int x = (int)rawX;
        int y = height.getAsInt() - 1 - (int)rawY;

        realId[index] = pointerId;
        touchX[index] = x;
        touchY[index] = y;
        deltaX[index] = 0;
        deltaY[index] = 0;
        pressure[index] = rawPressure;
        touched[index] = true;

        sink.touchDown(index, x, y);
        justTouched = true;
    }

    @Override
    public void pointerUp(int pointerId, float rawX, float rawY) {
        int index = lookUpPointerIndex(pointerId);
        if (index == -1) return;

        int x = (int)rawX;
        int y = height.getAsInt() - 1 - (int)rawY;

        realId[index] = -1;
        touchX[index] = x;
        touchY[index] = y;
        deltaX[index] = 0;
        deltaY[index] = 0;
        pressure[index] = 0f;
        touched[index] = false;

        sink.touchUp(index, x, y);
    }

    @Override
    public void pointerMove(int pointerId, float rawX, float rawY, float rawPressure) {
        int index = lookUpPointerIndex(pointerId);
        int x = (int)rawX;
        int y = height.getAsInt() - 1 - (int)rawY;

        if (index == -1) {
            if (x == mouseLastX && (int)rawY == mouseLastY) return;

            deltaX[0] = x - mouseLastX;
            deltaY[0] = -((int)rawY - mouseLastY);
            touchX[0] = x;
            touchY[0] = y;
            mouseLastX = x;
            mouseLastY = (int)rawY;
            sink.pointerMove(-1, x, y);
            return;
        }

        // arc's own Android computation: the game must read the same values as on vanilla Android.
        deltaX[index] = x - touchX[index];
        deltaY[index] = -((int)rawY - touchY[index]);
        touchX[index] = x;
        touchY[index] = y;
        pressure[index] = rawPressure;

        sink.pointerMove(index, x, y);
    }

    @Override
    public void pointerCancel() {
        // no synthetic release: the game's tap handling would turn a cancel into a click where the finger was
        clearPointers();
    }

    @Override
    public void scroll(float scrollY) {
        sink.scroll(scrollY);
    }

    @Override
    public void keyDown(int deviceCode, int repeat) {
        sink.keyDown(deviceCode, repeat);
    }

    @Override
    public void keyUp(int deviceCode, WireCharBuffer chars) {
        sink.keyUp(deviceCode, chars);
    }

    @Override
    public void text(WireCharBuffer chars) {
        sink.text(chars);
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

    public void clearPointers() {
        for (int i = 0; i < MAX_POINTERS; i++) {
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
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (realId[i] == -1) return i;
        }
        return -1;
    }

    private int lookUpPointerIndex(int pointerId) {
        for (int i = 0; i < MAX_POINTERS; i++) {
            if (realId[i] == pointerId) return i;
        }
        return -1;
    }

    public int mouseX(int pointer) {
        return touchX[pointer];
    }

    public int mouseY(int pointer) {
        return touchY[pointer];
    }

    public int deltaX(int pointer) {
        return deltaX[pointer];
    }

    public int deltaY(int pointer) {
        return deltaY[pointer];
    }

    public boolean isTouched(int pointer) {
        return touched[pointer];
    }

    public boolean isTouched() {
        for (int pointer = 0; pointer < MAX_POINTERS; pointer++) {
            if (touched[pointer]) return true;
        }
        return false;
    }

    public boolean justTouched() {
        return justTouched;
    }

    public float pressure(int pointer) {
        return pressure[pointer];
    }

    public long currentEventTime() {
        return currentEventTimeStamp;
    }
}
