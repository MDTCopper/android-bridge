package copper.bridge.util;

/**
 * Thrown when a batch frame does not match the schema that reads it: a length that runs past the end of the
 * blob, a record id the schema does not know, a field whose count does not fit. The reader catches it, logs
 * one line and drops the rest of the frame - a stream that cannot be trusted must not be walked further.
 */
public class BatchFormatException extends RuntimeException {
    public BatchFormatException(String message) {
        super(message);
    }
}
