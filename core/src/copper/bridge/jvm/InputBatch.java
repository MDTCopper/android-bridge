package copper.bridge.jvm;

import copper.bridge.annotation.*;
import copper.bridge.util.*;

/**
 * One batch channel: a frame of input events, packed by ART and consumed by the game loop. This class is the
 * schema - one kind of event is one abstract method, and the record id is that kind - so nothing inside a record
 * says what it is, and the branch implementing the methods keeps its handling in step with the frame it reads.
 * The receiver polls and never wakes, which is why a batch is the only thing written every frame: a frame costs
 * one crossing, not one per event. The buffers a reader sees are borrowed from the block the last poll returned
 * and rebound by the next poll.
 */
@ArtBatchHandler(maxBatches = 64, maxBytes = 1 << 20)
public abstract class InputBatch {
    /**
     * One finger starts touching. The pointer id is Android's own so a later move or up can find the
     * finger again; the coordinates are raw because the reading side is the one that knows the
     * surface height and flips them.
     */
    public abstract void pointerDown(int pointerId, float x, float y, float pressure);

    /** One tracked finger lifted. */
    public abstract void pointerUp(int pointerId, float x, float y);

    /** One finger moved, which is also what a mouse or a stylus hover arrives as. */
    public abstract void pointerMove(int pointerId, float x, float y, float pressure);

    /**
     * Every pointer is gone: the activity lost focus, so no up will follow. Its own kind rather than
     * an up per finger, because turning a cancel into a release would make it a click where the
     * finger happened to be.
     */
    public abstract void pointerCancel();

    /** A scroll wheel or a trackpad, already signed the way the game expects it. */
    public abstract void scroll(float scrollY);

    /**
     * A key went down. The repeat count travels with it because Android keeps sending a down while a
     * key is held, and only the reading side knows that the first one is the press.
     */
    public abstract void keyDown(int keyCode, int repeat);

    /**
     * A key came up, carrying the text its press produced. The text is a borrowed view into the frame
     * being read, so a reader that only types the characters never copies them; {@code String} is the
     * form a caller writes it with.
     */
    @SenderSignature({int.class, String.class})
    public abstract void keyUp(int keyCode, WireCharBuffer chars);

    /**
     * Text that is not a key transition, which is how Android reports multi character input; it must
     * not touch the reading side's idea of which keys are down.
     */
    @SenderSignature(String.class)
    public abstract void text(WireCharBuffer chars);
}
