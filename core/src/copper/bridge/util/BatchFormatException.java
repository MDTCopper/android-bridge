package copper.bridge.util;

/**
 * Thrown when a batch frame does not match the schema that reads it.
 *
 * <p>A frame is a byte stream that a schema describes; anything else - a length that runs past the end of the
 * blob, a record id the schema does not know, a field whose count does not fit - is a malformed frame rather
 * than a programming error at the call site. The reader catches this, logs one line, and drops the rest of the
 * frame: a stream that cannot be trusted must not be walked further.</p>
 */
public class BatchFormatException extends RuntimeException {
    public BatchFormatException(String message) {
        super(message);
    }
}
