package copper.bridge.gen.batch;

import copper.bridge.gen.Side;
import java.util.ArrayList;
import java.util.List;

/**
 * One batch channel: a schema class, its header and its records. One class is one channel, so direction, bounds and
 * locking hang off the declaration itself.
 */
final class Schema {
    String owner;
    Side receiver;
    boolean withLock;
    int maxBatches;
    int maxBytes;
    String accessor;
    /** The channel id, assigned by sorting the accessor names so it cannot drift between runs. */
    int id;
    final List<Field> headers = new ArrayList<>();
    final List<Record> records = new ArrayList<>();

    Side sender() {
        return receiver.caller();
    }

    String constant() {
        return "CHANNEL_" + accessor.toUpperCase(java.util.Locale.ROOT);
    }
}
