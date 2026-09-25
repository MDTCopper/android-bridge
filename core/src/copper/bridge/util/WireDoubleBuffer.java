package copper.bridge.util;

import java.nio.DoubleBuffer;

/**
 * A re-bindable view of a run of 64 bit floats: the IEEE-754 bit pattern, little-endian, for the same reason
 * the 32 bit one is - a value that has to survive a round trip must not be re-rounded on the way.
 */
public final class WireDoubleBuffer extends WireBuffer {
    public WireDoubleBuffer() {
        super(8);
    }

    @Override
    public WireDoubleBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireDoubleBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireDoubleBuffer flip() {
        super.flip();
        return this;
    }

    /** The element at an index. */
    public double get(int element) {
        final int at = index(element);
        long bits = 0;
        for (int i = 7; i >= 0; i--)
            bits = (bits << 8) | (hb[at + i] & 0xFFL);
        return Double.longBitsToDouble(bits);
    }

    /** The element at the cursor, which then advances. */
    public double get() {
        return get(position++);
    }

    /** Writes one element. */
    public WireDoubleBuffer put(int element, double value) {
        final int at = index(element);
        final long bits = Double.doubleToRawLongBits(value);
        for (int i = 0; i < 8; i++)
            hb[at + i] = (byte) ((bits >>> (8 * i)) & 0xFF);
        return this;
    }

    /** Writes one element at the cursor, which then advances. */
    public WireDoubleBuffer put(double value) {
        return put(position++, value);
    }

    /** Takes the whole array as this buffer's content, copying it. */
    public WireDoubleBuffer set(double[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 8], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    /** Takes what is left of a {@code java.nio} buffer. */
    public WireDoubleBuffer set(DoubleBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 8], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    /** Takes another wire buffer's content, copying it. */
    public WireDoubleBuffer set(WireDoubleBuffer values) {
        if (values == null)
            return set((double[]) null);
        final int count = values.limit();
        bind(new byte[count * 8], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }
}
