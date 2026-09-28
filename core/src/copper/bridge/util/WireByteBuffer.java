package copper.bridge.util;

import java.nio.ByteBuffer;

/**
 * A re-bindable view of a run of bytes, read and written by type. Element and wire form are the same byte;
 * every accessor is little-endian, the only order this bridge uses.
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

    public byte get(int element) {
        return hb[index(element)];
    }

    public byte get() {
        return hb[index(position++)];
    }

    public WireByteBuffer put(int element, byte value) {
        hb[index(element)] = value;
        return this;
    }

    public WireByteBuffer put(byte value) {
        hb[index(position++)] = value;
        return this;
    }

    public char getChar() {
        final int at = index(position, 2);
        position += 2;
        return (char) ((hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8));
    }

    public WireByteBuffer putChar(char value) {
        final int at = index(position, 2);
        position += 2;
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    public short getShort() {
        final int at = index(position, 2);
        position += 2;
        return (short) ((hb[at] & 0xFF) | (hb[at + 1] << 8));
    }

    public WireByteBuffer putShort(short value) {
        final int at = index(position, 2);
        position += 2;
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    public int getInt() {
        final int at = index(position, 4);
        position += 4;
        return (hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8) | ((hb[at + 2] & 0xFF) << 16)
                | ((hb[at + 3] & 0xFF) << 24);
    }

    public WireByteBuffer putInt(int value) {
        final int at = index(position, 4);
        position += 4;
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        hb[at + 2] = (byte) ((value >>> 16) & 0xFF);
        hb[at + 3] = (byte) ((value >>> 24) & 0xFF);
        return this;
    }

    public long getLong() {
        final int at = index(position, 8);
        position += 8;
        long value = 0;
        for (int i = 7; i >= 0; i--)
            value = (value << 8) | (hb[at + i] & 0xFFL);
        return value;
    }

    public WireByteBuffer putLong(long value) {
        final int at = index(position, 8);
        position += 8;
        for (int i = 0; i < 8; i++)
            hb[at + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        return this;
    }

    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    public WireByteBuffer putFloat(float value) {
        return putInt(Float.floatToRawIntBits(value));
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public WireByteBuffer putDouble(double value) {
        return putLong(Double.doubleToRawLongBits(value));
    }

    /**
     * The string at the cursor, which then advances past it. Layout is a 32 bit character count followed by the
     * characters - the same bytes a character sequence writes, so a declaration may carry its text either way.
     * Reading allocates; a caller that only wants the characters uses {@link #getChar()}.
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

    /** Writes a character sequence at the cursor; {@code null} writes nothing. Takes a {@link CharSequence}, so a
     * borrowed view such as {@link WireCharBuffer} needs no decoded copy. */
    public WireByteBuffer putString(CharSequence value) {
        final int count = value == null ? 0 : value.length();
        putInt(count);
        for (int i = 0; i < count; i++)
            putChar(value.charAt(i));
        return this;
    }

    /** Every {@code set} overload copies into an array of this instance's own; {@code null} empties it. */
    public WireByteBuffer set(byte[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count], 0, count);
        if (count > 0)
            System.arraycopy(values, 0, hb, 0, count);
        return this;
    }

    /** Takes what is left of a {@code java.nio} buffer. */
    public WireByteBuffer set(ByteBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count], 0, count);
        for (int i = 0; i < count; i++)
            hb[i] = values.get();
        return this;
    }

    public WireByteBuffer set(WireByteBuffer values) {
        if (values == null)
            return set((byte[]) null);
        final int count = values.limit();
        bind(new byte[count], 0, count);
        System.arraycopy(values.hb, values.offset, hb, 0, count);
        return this;
    }
}
