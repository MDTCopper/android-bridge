package copper.bridge.gen.bus;

import copper.bridge.gen.Names;
import copper.bridge.gen.TypeRef;

/** One outcome of an asynchronous request: {@code result(String[])} on a handler annotation. */
final class Callback {
    String name;
    TypeRef payload;
    Row answer;
    Row request;

    String setter() {
        return "on" + Names.capitalize(name);
    }

    String fire() {
        return "fire" + Names.capitalize(name);
    }

    String wireName(String request) {
        return request + Names.capitalize(name);
    }
}
