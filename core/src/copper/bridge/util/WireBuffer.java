package copper.bridge.util;

/**
 * What every wire buffer shares: one byte array, a byte offset, an element count, and the cursor. Ported from
 * {@code java.nio}'s little-endian buffers, because such a buffer cannot be re-pointed and a view of a new range
 * would allocate every frame. Every bounds check throws {@link BatchFormatException}.
 */
abstract class WireBuffer {
    private final int width;

    byte[] hb;
    int offset;
    int limit;
    int position;

    WireBuffer(int width) {
        this.width = width;
    }

    final int width() {
        return width;
    }

    /** Re-points this instance at a range of an array; the same instance serves every frame. */
    WireBuffer bind(byte[] array, int byteOffset, int elementCount) {
        if (array == null)
            throw new BatchFormatException("a wire buffer needs an array to point at");
        if (byteOffset < 0 || elementCount < 0
                || byteOffset + (long) elementCount * width > array.length)
            throw new BatchFormatException("the range " + byteOffset + " + " + elementCount + " x "
                    + width + " does not fit an array of " + array.length);
        this.hb = array;
        this.offset = byteOffset;
        this.limit = elementCount;
        this.position = 0;
        return this;
    }

    public final int capacity() {
        return limit;
    }

    public final int limit() {
        return limit;
    }

    public final int position() {
        return position;
    }

    public WireBuffer position(int element) {
        if (element < 0 || element > limit)
            throw new BatchFormatException("position " + element + " is outside 0.." + limit);
        position = element;
        return this;
    }

    public WireBuffer skip(int elements) {
        return position(position + elements);
    }

    public final int remaining() {
        return limit - position;
    }

    public WireBuffer clear() {
        position = 0;
        return this;
    }

    public WireBuffer flip() {
        limit = position;
        position = 0;
        return this;
    }

    public final boolean hasArray() {
        return hb != null;
    }

    /** The array this buffer points at, or {@code null} while it is unbound. */
    public final byte[] array() {
        return hb;
    }

    public final int arrayOffset() {
        return offset;
    }

    final int index(int element) {
        if (element < 0 || element >= limit)
            throw new BatchFormatException("element " + element + " is outside 0.." + (limit - 1));
        return offset + element * width;
    }

    /** The byte offset of a run of elements, all of them checked: an {@code int} through a byte view spans four, so
     *  checking the first alone would let the others run past the end. */
    final int index(int element, int count) {
        if (element < 0 || count < 0 || element + (long) count > limit)
            throw new BatchFormatException("elements " + element + " + " + count
                    + " are outside 0.." + limit);
        return offset + element * width;
    }

    /** Copies this buffer's elements into an array and answers the byte count; throws when unbound or too short. */
    public final int writeTo(byte[] destination, int destinationOffset) {
        if (hb == null)
            throw new BatchFormatException("this buffer is not bound to an array");
        final int bytes = limit * width;
        if (destinationOffset < 0 || destinationOffset + (long) bytes > destination.length)
            throw new BatchFormatException("writing " + bytes + " bytes at " + destinationOffset
                    + " does not fit an array of " + destination.length);
        System.arraycopy(hb, offset, destination, destinationOffset, bytes);
        return bytes;
    }
}
