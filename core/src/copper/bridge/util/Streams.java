package copper.bridge.util;

import java.io.*;

/** Stream helpers that avoid {@code InputStream.readAllBytes()}, which is missing on older Android API levels. */
public class Streams {
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
                // Fill each buffer before comparing: streams hand out different chunk sizes, so comparing
                // partial reads would report a false mismatch.
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

                if (read1 != read2) {
                    return false;
                }

                if (read1 == 0) {
                    return true;
                }

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
