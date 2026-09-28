package copper.bridge.util;

import java.nio.ShortBuffer;

/**
 * A re-bindable view of a run of 16 bit signed integers: little-endian like every wire buffer, and
 * sign-extended on the way out so a negative value stays negative.
 */
public final class WireShortBuffer extends WireBuffer {
    public WireShortBuffer() {
        super(2);
    }

    @Override
    public WireShortBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireShortBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireShortBuffer flip() {
        super.flip();
        return this;
    }

    public short get(int element) {
        final int at = index(element);
        return (short) ((hb[at] & 0xFF) | (hb[at + 1] << 8));
    }

    public short get() {
        return get(position++);
    }

    public WireShortBuffer put(int element, short value) {
        final int at = index(element);
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        return this;
    }

    public WireShortBuffer put(short value) {
        return put(position++, value);
    }

    public WireShortBuffer set(short[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    public WireShortBuffer set(ShortBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    public WireShortBuffer set(WireShortBuffer values) {
        if (values == null)
            return set((short[]) null);
        final int count = values.limit();
        bind(new byte[count * 2], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }
}
