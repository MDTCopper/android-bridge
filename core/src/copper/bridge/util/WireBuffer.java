package copper.bridge.util;

/**
 * What every wire buffer shares: one byte array, a byte offset, an element count, and the cursor.
 *
 * <p>A port of {@code java.nio}'s little-endian buffers rather than a wrapper: a {@code java.nio}
 * buffer cannot be re-pointed, so a view of a new range would allocate every frame - {@link #bind} is
 * what avoids that. Only little-endian exists, and every bounds check lives here and fails with a
 * {@link BatchFormatException}, so a malformed frame is a value the reader can catch and drop rather
 * than an index out of bounds further along.</p>
 */
abstract class WireBuffer {
    /** Bytes per element; the only difference between the concrete buffers. */
    private final int width;

    /** The backing array: the polled blob when reading, a staging buffer when writing. */
    byte[] hb;
    /** The byte offset of element zero; {@link #limit} and {@link #position} are in elements. */
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

    /** The number of elements this buffer sees; it does not change when the cursor moves. */
    public final int capacity() {
        return limit;
    }

    public final int limit() {
        return limit;
    }

    public final int position() {
        return position;
    }

    /** Moves the cursor to an element index, checked against the limit. */
    public WireBuffer position(int element) {
        if (element < 0 || element > limit)
            throw new BatchFormatException("position " + element + " is outside 0.." + limit);
        position = element;
        return this;
    }

    /** Moves the cursor forward, for stepping over a run the caller does not read. */
    public WireBuffer skip(int elements) {
        return position(position + elements);
    }

    /** How many elements are left before the limit. */
    public final int remaining() {
        return limit - position;
    }

    /** Moves the cursor back to the first element. */
    public WireBuffer clear() {
        position = 0;
        return this;
    }

    /** The cursor becomes the limit and the limit zero, as {@code java.nio} spells it. */
    public WireBuffer flip() {
        limit = position;
        position = 0;
        return this;
    }

    public final boolean hasArray() {
        return hb != null;
    }

    /** The array this buffer points at, or {@code null}. */
    public final byte[] array() {
        return hb;
    }

    /** The byte offset of element zero inside {@link #array()}. */
    public final int arrayOffset() {
        return offset;
    }

    /** The byte offset of one element, checked against the limit. */
    final int index(int element) {
        if (element < 0 || element >= limit)
            throw new BatchFormatException("element " + element + " is outside 0.." + (limit - 1));
        return offset + element * width;
    }

    /**
     * The byte offset of a run of elements, all of them checked: an {@code int} through a byte view
     * spans four elements, so checking only the first would let the other three run past the end.
     */
    final int index(int element, int count) {
        if (element < 0 || count < 0 || element + (long) count > limit)
            throw new BatchFormatException("elements " + element + " + " + count
                    + " are outside 0.." + limit);
        return offset + element * width;
    }

    /**
     * Copies this buffer's elements into an array and reports how many bytes that took; the encoder
     * writes the element count itself and then asks the value for its bytes.
     */
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
