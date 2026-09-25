package copper.bridge.util;

import java.nio.IntBuffer;

/**
 * A re-bindable view of a run of 32 bit signed integers, little-endian byte by byte, so the buffer means the
 * same thing on every device the two VMs run on rather than whatever the machine happens to prefer.
 */
public final class WireIntBuffer extends WireBuffer {
    public WireIntBuffer() {
        super(4);
    }

    @Override
    public WireIntBuffer bind(byte[] array, int byteOffset, int elementCount) {
        super.bind(array, byteOffset, elementCount);
        return this;
    }

    @Override
    public WireIntBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public WireIntBuffer flip() {
        super.flip();
        return this;
    }

    /** The element at an index. */
    public int get(int element) {
        final int at = index(element);
        return (hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8) | ((hb[at + 2] & 0xFF) << 16)
                | ((hb[at + 3] & 0xFF) << 24);
    }

    /** The element at the cursor, which then advances. */
    public int get() {
        return get(position++);
    }

    /** Writes one element. */
    public WireIntBuffer put(int element, int value) {
        final int at = index(element);
        hb[at] = (byte) (value & 0xFF);
        hb[at + 1] = (byte) ((value >>> 8) & 0xFF);
        hb[at + 2] = (byte) ((value >>> 16) & 0xFF);
        hb[at + 3] = (byte) ((value >>> 24) & 0xFF);
        return this;
    }

    /** Writes one element at the cursor, which then advances. */
    public WireIntBuffer put(int value) {
        return put(position++, value);
    }

    /** Takes the whole array as this buffer's content, copying it. */
    public WireIntBuffer set(int[] values) {
        final int count = values == null ? 0 : values.length;
        bind(new byte[count * 4], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values[i]);
        return this;
    }

    /** Takes what is left of a {@code java.nio} buffer. */
    public WireIntBuffer set(IntBuffer values) {
        final int count = values == null ? 0 : values.remaining();
        bind(new byte[count * 4], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get());
        return this;
    }

    /** Takes another wire buffer's content, copying it. */
    public WireIntBuffer set(WireIntBuffer values) {
        if (values == null)
            return set((int[]) null);
        final int count = values.limit();
        bind(new byte[count * 4], 0, count);
        for (int i = 0; i < count; i++)
            put(i, values.get(i));
        return this;
    }
}
