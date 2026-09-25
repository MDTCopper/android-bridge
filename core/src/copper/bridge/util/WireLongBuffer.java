package copper.bridge.util;

import java.nio.LongBuffer;

/**
 * A re-bindable view of a run of 64 bit signed integers, little-endian byte by byte. Also the type a native
 * window pointer travels as: it is 64 bit on every ABI this bridge supports, which is why it never goes
 * through an int.
 */
public final class WireLongBuffer extends WireBuffer {
    public WireLongBuffer() {
        super(8);
    }

    @Override
    public WireLongBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireLongBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireLongBuffer flip() {
        super.flip();
        return this;
    }

    /** The element at an index. */
    public long get(int element) {
        final int at = index(element);
        long value = 0;
        for (int i = 7; i >= 0; i--)
            value = (value << 8) | (hb[at + i] & 0xFFL);
        return value;
    }

    /** The element at the cursor, which then advances. */
    public long get() {
        return get(position++);
    }

    /** Writes one element. */
    public WireLongBuffer put(int element, long value) {
        final int at = index(element);
        for (int i = 0; i < 8; i++)
            hb[at + i] = (byte) ((value >>> (8 * i)) & 0xFF);
        return this;
    }

    /** Writes one element at the cursor, which then advances. */
    public WireLongBuffer put(long value) {
        return put(position++, value);
    }

    /** Takes the whole array as this buffer's content, copying it. */
    public WireLongBuffer set(long[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 8], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    /** Takes what is left of a {@code java.nio} buffer. */
    public WireLongBuffer set(LongBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 8], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    /** Takes another wire buffer's content, copying it. */
    public WireLongBuffer set(WireLongBuffer values) {
        if (values == null)
            return set((long[]) null);
        final int count = values.limit();
        bind(new byte[count * 8], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }
}
