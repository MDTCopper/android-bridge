package copper.bridge.gen.bus;

/** The four things a bus row can be. */
enum Channel {
    /** Queued to the ART main thread. A non-void result makes the caller block for the answer. */
    POST("Post"),
    /** Queued to the JVM game loop. */
    EVENT("Event"),
    /** Queued to the JVM game loop, carrying the id of the request it answers. */
    ANSWER("Answer"),
    /** Not queued at all: performed on the calling thread. */
    DIRECT("Direct");

    /** The enumerator the generated C++ uses. */
    final String cpp;

    Channel(String cpp) {
        this.cpp = cpp;
    }

    boolean pumped() {
        return this != DIRECT;
    }
}
