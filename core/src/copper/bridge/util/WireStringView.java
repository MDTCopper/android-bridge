package copper.bridge.util;

/**
 * A re-bindable view of a run of strings inside a frame.
 *
 * <p>Not a list of character buffers: one object that walks the layout is what keeps a reader from
 * allocating a buffer per string per frame. The layout is {@code [u32 count]} followed by {@code count}
 * entries of {@code [u32 length][characters]} - the same shape a sequence of characters has, nested.
 * {@link #get(int)} allocates the string it returns, because that is what a string is; a reader that only
 * needs the characters should use {@link #get(int, char[])} instead.</p>
 */
public final class WireStringView {
    private byte[] hb;
    private int offset;
    private int count;

    /** Points this view at the count and the strings that follow it. */
    public WireStringView bind(byte[] array, int byteOffset) {
        if (array == null)
            throw new BatchFormatException("a string view needs an array to point at");
        if (byteOffset < 0 || byteOffset + 4 > array.length)
            throw new BatchFormatException("no room for a count at " + byteOffset
                    + " in an array of " + array.length);
        this.hb = array;
        this.offset = byteOffset;
        this.count = readInt(byteOffset);
        if (count < 0)
            throw new BatchFormatException("a string view cannot hold " + count + " strings");
        // Each entry needs at least its own length, so a count that cannot even fit that many is rejected
        // here rather than on the first read: a malformed frame should fail where it is bound.
        if (byteOffset + 4 + 4L * count > array.length)
            throw new BatchFormatException("a view of " + count + " strings does not fit an array of "
                    + array.length);
        return this;
    }

    /** How many strings the view holds. */
    public int size() {
        return count;
    }

    /** The string at an index. */
    public String get(int index) {
        final int length = lengthOf(index);
        final char[] text = new char[length];
        read(index, text);
        return new String(text);
    }

    /**
     * Copies the string at an index into an array, and reports its length.
     *
     * @return the number of characters written, which may be shorter than the array
     */
    public int get(int index, char[] destination) {
        final int length = lengthOf(index);
        final int written = Math.min(length, destination.length);
        read(index, destination, written);
        return length;
    }

    /** The length of one entry, checked against the end of the array. */
    private int lengthOf(int index) {
        if (index < 0 || index >= count)
            throw new BatchFormatException("string " + index + " is outside 0.." + (count - 1));
        int at = offset + 4;
        for (int i = 0; i < index; i++) {
            final int length = readInt(at);
            at += 4 + 2 * length;
            if (at > hb.length)
                throw new BatchFormatException("string " + i + " runs past the end of the frame");
        }
        final int length = readInt(at);
        if (length < 0 || at + 4 + 2L * length > hb.length)
            throw new BatchFormatException("string " + index + " runs past the end of the frame");
        return length;
    }

    private void read(int index, char[] destination) {
        read(index, destination, destination.length);
    }

    private void read(int index, char[] destination, int written) {
        int at = offset + 4;
        for (int i = 0; i < index; i++)
            at += 4 + 2 * readInt(at);
        at += 4;
        for (int i = 0; i < written; i++)
            destination[i] = (char) ((hb[at + 2 * i] & 0xFF) | ((hb[at + 2 * i + 1] & 0xFF) << 8));
    }

    private int readInt(int at) {
        return (hb[at] & 0xFF) | ((hb[at + 1] & 0xFF) << 8) | ((hb[at + 2] & 0xFF) << 16)
                | ((hb[at + 3] & 0xFF) << 24);
    }
}
