package copper.bridge.util;

import java.nio.CharBuffer;

/**
 * A re-bindable view of a run of 16 bit characters, little-endian like every wire buffer: the low byte comes
 * first, and a {@code char} is unsigned on both sides, which is why the decoding masks rather than sign-extends.
 * It is also how a string travels inside a frame. It is a {@link CharSequence}, so a reader that needs no decoded
 * copy can hand the view itself to anything that takes one; only operations that produce a value
 * ({@link #text()}, {@link #toString()}) copy, and the sequence is valid only until the next poll rebinds it.
 */
public final class WireCharBuffer extends WireBuffer implements CharSequence {
    public WireCharBuffer() {
        super(2);
    }

    @Override
    public WireCharBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireCharBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireCharBuffer flip() {
        super.flip();
        return this;
    }

    /** The number of characters this view sees. */
    @Override
    public int length() {
        return limit;
    }

    /**
     * The character at an index.
     *
     * <p>A {@code CharSequence} has to answer an out of range index with
     * {@link IndexOutOfBoundsException}, so this checks the range itself and leaves the wire layer's
     * {@code BatchFormatException} to {@link #get(int)}, which is the accessor a decoder uses.</p>
     */
    @Override
    public char charAt(int index) {
        if (index < 0 || index >= limit)
            throw new IndexOutOfBoundsException("index " + index + " is outside 0.." + (limit - 1));
        return get(index);
    }

    /**
     * A slice of this view, as a string.
     *
     * <p>The one {@code CharSequence} operation that has to copy: a slice would be another view, and
     * a view that the next poll rebinds underneath whoever holds it is a trap. A reader that wants
     * characters without a copy reads them with {@link #get(int)} or walks {@link #charAt(int)}.</p>
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || end < start || end > limit)
            throw new IndexOutOfBoundsException("range " + start + ".." + end + " is outside 0.." + limit);
        final char[] slice = new char[end - start];
        for (int i = start; i < end; i++)
            slice[i - start] = get(i);
        return new String(slice);
    }

    /** The content as a string, which is what printing a character sequence means. */
    @Override
    public String toString() {
        return text();
    }

    /** The element at an index. */
    public char get(int element) {
        final int at = index(element);
        return (char) ((hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8));
    }

    /** The element at the cursor, which then advances. */
    public char get() {
        return get(position++);
    }

    /** Writes one element. */
    public WireCharBuffer put(int element, char value) {
        final int at = index(element);
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    /** Writes one element at the cursor, which then advances. */
    public WireCharBuffer put(char value) {
        return put(position++, value);
    }

    /** Takes the whole string as this buffer's content, copying its characters. */
    public WireCharBuffer set(String values) {
        final int count = values == null ? 0 : values.length();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.charAt(i));
        return this;
    }

    /** Takes the whole array as this buffer's content, copying it. */
    public WireCharBuffer set(char[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    /** Takes what is left of a {@code java.nio} buffer. */
    public WireCharBuffer set(CharBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    /** Takes another wire buffer's content, copying it. */
    public WireCharBuffer set(WireCharBuffer values) {
        if (values == null)
            return set((String) null);
        final int count = values.limit();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }

    /**
     * The content as a string.
     *
     * <p>Allocates, so a reader that only needs a character or two should use {@link #get(int)}
     * instead; the batch contract is that the buffers are borrowed, and building a string from one
     * is a copy by definition.</p>
     */
    public String text() {
        if (hb == null)
            return "";
        final char[] text = new char[limit];
        for (int i = 0; i < limit; i++)
            text[i] = get(i);
        return new String(text);
    }
}
