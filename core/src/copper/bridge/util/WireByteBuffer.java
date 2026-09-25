package copper.bridge.util;

import java.nio.ByteBuffer;

/**
 * A re-bindable view of a run of bytes, read and written by type.
 *
 * <p>The element is a byte, so the wire form and the memory form are the same. What it adds is the shape
 * {@code java.nio.ByteBuffer} has - the cursor moves by the width of the value written or read, so a frame
 * can be walked field by field without the caller tracking offsets - and every accessor is little-endian,
 * the only order this bridge uses.</p>
 */
public final class WireByteBuffer extends WireBuffer {
    public WireByteBuffer() {
        super(1);
    }

    @Override
    public WireByteBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireByteBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireByteBuffer flip() {
        super.flip();
        return this;
    }

    /** The element at an index. */
    public byte get(int element) {
        return hb[index(element)];
    }

    /** The element at the cursor, which then advances. */
    public byte get() {
        return hb[index(position++)];
    }

    /** Writes one element. */
    public WireByteBuffer put(int element, byte value) {
        hb[index(element)] = value;
        return this;
    }

    /** Writes one element at the cursor, which then advances. */
    public WireByteBuffer put(byte value) {
        hb[index(position++)] = value;
        return this;
    }

    /** The 16 bit character at the cursor, which then advances past it. */
    public char getChar() {
        final int at = index(position, 2);
        position += 2;
        return (char) ((hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8));
    }

    /** Writes one 16 bit character at the cursor, which then advances past it. */
    public WireByteBuffer putChar(char value) {
        final int at = index(position, 2);
        position += 2;
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    /** The 16 bit integer at the cursor, which then advances past it. */
    public short getShort() {
        final int at = index(position, 2);
        position += 2;
        return (short) ((hb[at] & 0xFF) | (hb[at + 1] << 8));
    }

    /** Writes one 16 bit integer at the cursor, which then advances past it. */
    public WireByteBuffer putShort(short value) {
        final int at = index(position, 2);
        position += 2;
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    /** The 32 bit integer at the cursor, which then advances past it. */
    public int getInt() {
        final int at = index(position, 4);
        position += 4;
        return (hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8) | ((hb[at + 2] & 0xFF) << 16)
                | ((hb[at + 3] & 0xFF) << 24);
    }

    /** Writes one 32 bit integer at the cursor, which then advances past it. */
    public WireByteBuffer putInt(int value) {
        final int at = index(position, 4);
        position += 4;
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        hb[at + 2] = (byte) ((value >>> 16) & 0xFF);
        hb[at + 3] = (byte) ((value >>> 24) & 0xFF);
        return this;
    }

    /** The 64 bit integer at the cursor, which then advances past it. */
    public long getLong() {
        final int at = index(position, 8);
        position += 8;
        long value = 0;
        for (int i = 7; i >= 0; i--)
            value = (value << 8) | (hb[at + i] & 0xFFL);
        return value;
    }

    /** Writes one 64 bit integer at the cursor, which then advances past it. */
    public WireByteBuffer putLong(long value) {
        final int at = index(position, 8);
        position += 8;
        for (int i = 0; i < 8; i++)
            hb[at + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        return this;
    }

    /** The 32 bit float at the cursor, which then advances past it. */
    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    /** Writes one 32 bit float at the cursor, which then advances past it. */
    public WireByteBuffer putFloat(float value) {
        return putInt(Float.floatToRawIntBits(value));
    }

    /** The 64 bit float at the cursor, which then advances past it. */
    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    /** Writes one 64 bit float at the cursor, which then advances past it. */
    public WireByteBuffer putDouble(double value) {
        return putLong(Double.doubleToRawLongBits(value));
    }

    /**
     * The string at the cursor, which then advances past it. The layout is a 32 bit character count
     * followed by the characters - the same bytes a sequence of characters writes, so a declaration may
     * carry its text either way and the other side reads the same frame. Reading allocates, because that is
     * what a string is; a caller that only wants the characters should read them one at a time with
     * {@link #getChar()}.
     */
    public String getString() {
        final int count = getInt();
        if (count < 0 || (long) count * 2 > remaining())
            throw new BatchFormatException("a string of " + count + " characters does not fit the "
                    + remaining() + " bytes left");
        final char[] text = new char[count];
        for (int i = 0; i < count; i++)
            text[i] = getChar();
        return new String(text);
    }

    /**
     * Writes one character sequence at the cursor, which then advances past it; {@code null} writes nothing.
     * Takes a {@link CharSequence} rather than a {@code String} so a caller holding a borrowed view such as
     * {@link WireCharBuffer} can write it without decoding it into a copy first.
     */
    public WireByteBuffer putString(CharSequence value) {
        final int count = value == null ? 0 : value.length();
        putInt(count);
        for (int i = 0; i < count; i++)
            putChar(value.charAt(i));
        return this;
    }

    /** Takes the whole array as this buffer's content, copying it into an array of its own. */
    public WireByteBuffer set(byte[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count], 0, count);
        if (count > 0)
            System.arraycopy(values, 0, hb, 0, count);
        return this;
    }

    /** Takes what is left of a {@code java.nio} buffer, for a caller that already holds one. */
    public WireByteBuffer set(ByteBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count], 0, count);
        for (int i = 0; i < count; i++)
            hb[i] = values.get();
        return this;
    }

    /** Takes another wire buffer's content, copying it. */
    public WireByteBuffer set(WireByteBuffer values) {
        if (values == null)
            return set((byte[]) null);
        final int count = values.limit();
        bind(new byte[count], 0, count);
        System.arraycopy(values.hb, values.offset, hb, 0, count);
        return this;
    }
}
