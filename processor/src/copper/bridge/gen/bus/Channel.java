package copper.bridge.gen.bus;

/** The four things a bus row can be. */
enum Channel {
    /** Queued to the ART main thread. A non-void result makes the caller block for the answer. */
    POST("Post"),
    EVENT("Event"),
    ANSWER("Answer"),
    /** Not queued at all: performed on the calling thread. */
    DIRECT("Direct");

    final String cpp;

    Channel(String cpp) {
        this.cpp = cpp;
    }

    boolean pumped() {
        return this != DIRECT;
    }
}
