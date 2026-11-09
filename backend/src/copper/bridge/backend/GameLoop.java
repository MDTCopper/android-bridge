package copper.bridge.backend;

/**
 * The frame loop shared by every branch: pump the bus and posted runnables, idle while paused or without a surface,
 * then run one frame and tell the host once the first frame is out. The branch supplies the frame body and the
 * event-state queries; the ordering and the first-frame handshake live here.
 */
public final class GameLoop {
    public interface Host {
        boolean running();

        boolean paused();

        boolean surfaceReady();

        /** Drain the event bus and the runnables posted to the game loop. */
        void pump();

        /** Wait without drawing while paused or without a surface. */
        void idle();

        /** One frame: begin, update, draw, swap. */
        void frame();

        /** Called once, after the first completed frame. */
        void firstFrame();
    }

    public void run(Host host) {
        boolean firstFrame = false;
        while (host.running()) {
            host.pump();

            if (host.paused() || !host.surfaceReady()) {
                // nothing may be drawn without a current surface, but events must keep flowing
                host.idle();
                continue;
            }

            host.frame();

            if (!firstFrame) {
                firstFrame = true;
                host.firstFrame();
            }
        }
    }
}
