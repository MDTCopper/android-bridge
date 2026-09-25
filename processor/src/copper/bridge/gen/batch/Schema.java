package copper.bridge.gen.batch;

import copper.bridge.gen.Side;
import java.util.ArrayList;
import java.util.List;

/**
 * One batch channel: a schema class, its header and its records.
 *
 * <p>One class is one channel, so direction, bounds and locking hang off the declaration itself.</p>
 */
final class Schema {
    /** The declaring class, as Java names it. */
    String owner;
    /** The side that receives, which is the side named by the annotation. */
    Side receiver;
    boolean withLock;
    int maxBatches;
    int maxBytes;
    /** The accessor name: the class name without its suffix, first letter in lower case. */
    String accessor;
    /** The channel id, assigned by sorting the accessor names so it cannot drift between runs. */
    int id;
    final List<Field> headers = new ArrayList<>();
    final List<Record> records = new ArrayList<>();

    /** The side that sends, which is the other one. */
    Side sender() {
        return receiver.caller();
    }

    /** The generated channel constant, e.g. {@code CHANNEL_INPUT}. */
    String constant() {
        return "CHANNEL_" + accessor.toUpperCase(java.util.Locale.ROOT);
    }
}
