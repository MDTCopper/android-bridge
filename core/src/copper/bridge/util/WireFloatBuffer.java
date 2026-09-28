package copper.bridge.util;

import java.nio.FloatBuffer;

/**
 * A re-bindable view of a run of 32 bit floats: the IEEE-754 bit pattern, little-endian, never the value
 * rounded or truncated to an integer. A coordinate has to arrive with the precision it was sampled with,
 * which is the whole reason a frame carries floats rather than fixed point.
 */
public final class WireFloatBuffer extends WireBuffer {
    public WireFloatBuffer() {
        super(4);
    }

    @Override
    public WireFloatBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireFloatBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireFloatBuffer flip() {
        super.flip();
        return this;
    }

    public float get(int element) {
        final int at = index(element);
        return Float.intBitsToFloat((hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8)
                | ((hb[at + 2] & 0xFF) << 16) | ((hb[at + 3] & 0xFF) << 24));
    }

    public float get() {
        return get(position++);
    }

    public WireFloatBuffer put(int element, float value) {
        final int at = index(element);
        final int bits = Float.floatToRawIntBits(value);
        hb[at] = (byte) (bits & 0xFF);
        hb[at + 1] = (byte) ((bits >>> 8) & 0xFF);
        hb[at + 2] = (byte) ((bits >>> 16) & 0xFF);
        hb[at + 3] = (byte) ((bits >>> 24) & 0xFF);
        return this;
    }

    public WireFloatBuffer put(float value) {
        return put(position++, value);
    }

    public WireFloatBuffer set(float[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 4], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    public WireFloatBuffer set(FloatBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 4], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    public WireFloatBuffer set(WireFloatBuffer values) {
        if (values == null)
            return set((float[]) null);
        final int count = values.limit();
        bind(new byte[count * 4], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }
}
