package copper.bridge.util;

import java.io.*;

/**
 * Stream helpers that avoid {@code InputStream.readAllBytes()}, which is missing on older Android
 * API levels.
 */
public class Streams {
    /** Reads the whole stream into a byte array. */
    public static byte[] readAllBytes(InputStream in) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int len;
            while ((len = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            return buffer.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /** Copies everything from one stream to another, closing the input when asked to. */
    public static void pipeStream(InputStream in, OutputStream out, boolean closeInput) {
        try {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (closeInput)
                    in.close();
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Compares the remaining content of two input streams for equality.
     * This method consumes both streams until a mismatch is found or both reach the end.
     * It does not close the streams.
     */
    public static boolean contentEquals(InputStream in1, InputStream in2) {
        if (in1 == in2) {
            return true;
        }
        if (in1 == null || in2 == null) {
            return false;
        }

        try {
            byte[] buffer1 = new byte[8192];
            byte[] buffer2 = new byte[8192];

            while (true) {
                int read1 = 0;
                // Read until the buffer is full or the end of the stream is reached.
                // This prevents false negatives when streams read in different chunk sizes.
                while (read1 < buffer1.length) {
                    int r = in1.read(buffer1, read1, buffer1.length - read1);
                    if (r == -1) break;
                    read1 += r;
                }

                int read2 = 0;
                while (read2 < buffer2.length) {
                    int r = in2.read(buffer2, read2, buffer2.length - read2);
                    if (r == -1) break;
                    read2 += r;
                }

                // If the total bytes read in this block differ, the contents are not equal.
                if (read1 != read2) {
                    return false;
                }

                // If both streams reached the end simultaneously, they are identical.
                if (read1 == 0) {
                    return true;
                }

                // Compare the filled portion of the buffers byte by byte.
                for (int i = 0; i < read1; i++) {
                    if (buffer1[i] != buffer2[i]) {
                        return false;
                    }
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
