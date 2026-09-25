package copper.bridge.art;

import android.view.*;
import copper.bridge.gen.*;

/**
 * Turns Android input into the frames the JVM side consumes. A {@code MotionEvent} already carries a whole
 * frame's worth of pointer samples, so it is written into one frame and sent with one call; keys are sent
 * one event per frame, which is what a keyboard actually produces.
 *
 * <p>Nothing here interprets the events: the mapping onto the game's own key and pointer model happens on
 * the JVM side, where the game's own classes live, which keeps this class version independent.</p>
 */
public class BridgeInput implements View.OnTouchListener, View.OnGenericMotionListener {

    /**
     * Registers this instance as the listeners of the view it is given. The view is not stored: it is needed
     * for exactly these two calls, and everything sent afterwards goes through the generated accessor.
     */
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

    /** Forwards one key press, with the repeat count Android reports for a held key. */
    public boolean keyDown(int keyCode, int repeat) {
        ArtBatch.input.keyDown(keyCode, repeat);
        ArtBatch.submitInput();
        return true;
    }

    /** Forwards one key release together with the text its press produced. */
    public boolean keyUp(int keyCode, String chars) {
        ArtBatch.input.keyUp(keyCode, chars);
        ArtBatch.submitInput();
        return true;
    }

    /** Forwards text that is not a key transition, as Android reports multi character input. */
    public boolean text(String chars) {
        ArtBatch.input.text(chars);
        ArtBatch.submitInput();
        return true;
    }

    /**
     * The text one key event carries, or an empty string when it carries none. Only the up of a press types,
     * which is what arc's own Android backend does: a key down is a transition and the text belongs to the press
     * as a whole.
     *
     * <p>{@code KeyEvent.getCharacters()} is only filled for {@code ACTION_MULTIPLE}, so reading it on down and
     * up always produced null and the game's text fields never received a character. The unicode char is the
     * right source there, with backspace forced to {@code \b} because Android reports no unicode char for it.
     */
    @SuppressWarnings("deprecation")
    public static String characters(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            // multi character input really does arrive as text, and it is not a key transition
            String chars = event.getCharacters();
            return chars == null ? "" : chars;
        }
        if (event.getAction() != KeyEvent.ACTION_UP)
            return "";
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL)
            return "\b";

        // a modifier or an arrow key has no unicode char; getUnicodeChar() answers 0 for those, and
        // typing a NUL is not the same as typing nothing
        char character = (char) event.getUnicodeChar();
        return character == 0 ? "" : String.valueOf(character);
    }

    /** Clears the JVM side pointer state, used when the activity loses focus. */
    public void cancelAllPointers() {
        ArtBatch.input.pointerCancel();
        ArtBatch.submitInput();
    }

    /**
     * Writes a motion event into the current frame.
     *
     * @param generic whether the event came from the generic motion listener, which only carries
     *                hover and scroll data
     * @return the number of records written, or 0 when the event carries nothing actionable
     */
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

    /**
     * Writes every sample of a move event. A move carries the positions sampled since the last frame, so
     * replaying them in order is what preserves a fast swipe: one record per sample per pointer.
     */
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
