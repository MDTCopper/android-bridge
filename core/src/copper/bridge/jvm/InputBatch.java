package copper.bridge.jvm;

import copper.bridge.annotation.*;
import copper.bridge.util.*;

/**
 * One batch channel: a frame of input events, packed by ART and consumed by the game loop. The class is the schema,
 * so one kind of event is one abstract method and the record id is that kind; the receiver polls and never wakes,
 * which is why a frame costs one crossing.
 */
@ArtBatchHandler(maxBatches = 64, maxBytes = 1 << 20)
public abstract class InputBatch {
    /** One finger starts touching. The pointer id is Android's, so a later move or up finds the finger again. */
    public abstract void pointerDown(int pointerId, float x, float y, float pressure);

    public abstract void pointerUp(int pointerId, float x, float y);

    public abstract void pointerMove(int pointerId, float x, float y, float pressure);

    /** Every pointer is gone: the activity lost focus, so no up follows; a cancel is not a release. */
    public abstract void pointerCancel();

    public abstract void scroll(float scrollY);

    /** A key went down. The repeat count travels with it because Android keeps sending a down while a key is held. */
    public abstract void keyDown(int keyCode, int repeat);

    /** A key came up, carrying the text its press produced as a borrowed view, never copied. */
    @SenderSignature({int.class, String.class})
    public abstract void keyUp(int keyCode, WireCharBuffer chars);

    /** Text that is not a key transition; it must not touch the reading side's idea of which keys are down. */
    @SenderSignature(String.class)
    public abstract void text(WireCharBuffer chars);
}
