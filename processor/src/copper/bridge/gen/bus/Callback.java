package copper.bridge.gen.bus;

import copper.bridge.gen.Names;
import copper.bridge.gen.TypeRef;

/** One outcome of an asynchronous request: {@code result(String[])} on a handler annotation. */
final class Callback {
    /** The outcome's name as written, e.g. {@code result}. */
    String name;
    /** The single value it carries, or {@code null} for an outcome with no payload. */
    TypeRef payload;
    /** The row the outcome travels as. */
    Row answer;
    /** The request this outcome belongs to. */
    Row request;

    /** The chained method a caller registers it with, e.g. {@code onResult}. */
    String setter() {
        return "on" + Names.capitalize(name);
    }

    /** The delivery method generated for it, e.g. {@code fireResult}. */
    String fire() {
        return "fire" + Names.capitalize(name);
    }

    /** The bus name of the answer, e.g. {@code showFileChooserResult}. */
    String wireName(String request) {
        return request + Names.capitalize(name);
    }
}
