package copper.bridge.util;

import java.nio.CharBuffer;

/**
 * A re-bindable view of a run of 16 bit characters, little-endian like every wire buffer, and how a string travels
 * inside a frame. A {@link CharSequence}, so a reader that needs no decoded copy hands the view itself on; the
 * sequence is valid only until the next poll rebinds it.
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

    @Override
    public int length() {
        return limit;
    }

    /** Out of range answers {@link IndexOutOfBoundsException}, as a {@code CharSequence} must; {@link #get(int)} is
     * the accessor a decoder uses, and keeps the wire layer's {@link BatchFormatException}. */
    @Override
    public char charAt(int index) {
        if (index < 0 || index >= limit)
            throw new IndexOutOfBoundsException("index " + index + " is outside 0.." + (limit - 1));
        return get(index);
    }

    /** A slice as a string: the one {@code CharSequence} call that has to copy, because a view the next poll
     * rebinds underneath its holder is a trap. */
    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || end < start || end > limit)
            throw new IndexOutOfBoundsException("range " + start + ".." + end + " is outside 0.." + limit);
        final char[] slice = new char[end - start];
        for (int i = start; i < end; i++)
            slice[i - start] = get(i);
        return new String(slice);
    }

    @Override
    public String toString() {
        return text();
    }

    public char get(int element) {
        final int at = index(element);
        return (char) ((hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8));
    }

    public char get() {
        return get(position++);
    }

    public WireCharBuffer put(int element, char value) {
        final int at = index(element);
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    public WireCharBuffer put(char value) {
        return put(position++, value);
    }

    /** Every {@code set} overload copies into an array of this instance's own; {@code null} empties it. */
    public WireCharBuffer set(String values) {
        final int count = values == null ? 0 : values.length();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.charAt(i));
        return this;
    }

    public WireCharBuffer set(char[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    public WireCharBuffer set(CharBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    public WireCharBuffer set(WireCharBuffer values) {
        if (values == null)
            return set((String) null);
        final int count = values.limit();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }

    /** Builds a string, which allocates; a reader that wants a character or two uses {@link #get(int)}. */
    public String text() {
        if (hb == null)
            return "";
        final char[] text = new char[limit];
        for (int i = 0; i < limit; i++)
            text[i] = get(i);
        return new String(text);
    }
}
