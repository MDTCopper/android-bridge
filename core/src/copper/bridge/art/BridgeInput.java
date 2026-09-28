package copper.bridge.art;

import android.view.*;
import copper.bridge.gen.*;

/**
 * Turns Android input into the frames the JVM consumes. A {@code MotionEvent} already carries a whole frame's
 * pointer samples, so it is written into one frame and sent with one call. Nothing here interprets the events.
 */
public class BridgeInput implements View.OnTouchListener, View.OnGenericMotionListener {

    /** Registers this instance as the view's listeners; the view is not stored. */
    public BridgeInput(View view) {
        view.setOnTouchListener(this);
        view.setOnGenericMotionListener(this);
    }

    @Override
    public boolean onTouch(View source, MotionEvent event) {
        if (pack(event, false) > 0)
            ArtBatch.submitInput();
        return true;
    }

    @Override
    public boolean onGenericMotion(View source, MotionEvent event) {
        if (pack(event, true) > 0)
            ArtBatch.submitInput();
        return true;
    }

    public boolean keyDown(int keyCode, int repeat) {
        ArtBatch.input.keyDown(keyCode, repeat);
        ArtBatch.submitInput();
        return true;
    }

    public boolean keyUp(int keyCode, String chars) {
        ArtBatch.input.keyUp(keyCode, chars);
        ArtBatch.submitInput();
        return true;
    }

    public boolean text(String chars) {
        ArtBatch.input.text(chars);
        ArtBatch.submitInput();
        return true;
    }

    /** The text one key event carries, or an empty string. Only the up of a press types: {@code getCharacters()} is
     *  filled for {@code ACTION_MULTIPLE} only, and backspace is forced to {@code \b}. */
    @SuppressWarnings("deprecation")
    public static String characters(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            String chars = event.getCharacters();
            return chars == null ? "" : chars;
        }
        if (event.getAction() != KeyEvent.ACTION_UP)
            return "";
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL)
            return "\b";

        // a modifier or an arrow key has no unicode char: getUnicodeChar() answers 0, and a NUL is not nothing
        char character = (char) event.getUnicodeChar();
        return character == 0 ? "" : String.valueOf(character);
    }

    public void cancelAllPointers() {
        ArtBatch.input.pointerCancel();
        ArtBatch.submitInput();
    }

    /** Writes a motion event into the current frame; {@code generic} marks an event from the generic motion listener.
     *  Returns the records written, or 0 when there is nothing actionable. */
    private int pack(MotionEvent event, boolean generic) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        if (generic) {
            if (action == MotionEvent.ACTION_SCROLL) {
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                ArtBatch.input.scroll(-Math.signum(scrollY));
                return 1;
            }
            return action == MotionEvent.ACTION_HOVER_MOVE ? move(event) : 0;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                ArtBatch.input.pointerDown(pointerId, event.getX(pointerIndex), event.getY(pointerIndex),
                        event.getPressure(pointerIndex));
                return 1;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                ArtBatch.input.pointerUp(pointerId, event.getX(pointerIndex), event.getY(pointerIndex));
                return 1;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                return move(event);
            case MotionEvent.ACTION_CANCEL:
                ArtBatch.input.pointerCancel();
                return 1;
            default:
                return 0;
        }
    }

    /** Writes every sample of a move event, one record per sample per pointer, which preserves a fast swipe. */
    private int move(MotionEvent event) {
        int records = 0;
        int history = event.getHistorySize();
        int pointers = event.getPointerCount();
        for (int h = 0; h <= history; h++) {
            for (int p = 0; p < pointers; p++) {
                float x = h < history ? event.getHistoricalX(p, h) : event.getX(p);
                float y = h < history ? event.getHistoricalY(p, h) : event.getY(p);
                ArtBatch.input.pointerMove(event.getPointerId(p), x, y, event.getPressure(p));
                records++;
            }
        }
        return records;
    }
}
