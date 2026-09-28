package copper.bridge.v1;

import arc.*;
import arc.audio.*;
import arc.struct.*;
import arc.util.Threads;


import copper.bridge.*;
import copper.bridge.gen.*;
import copper.bridge.jvm.Surface;
import copper.bridge.util.*;

/**
 * arc's application object for the JVM side of the bridge. On Android arc is driven by ART - the activity owns the
 * lifecycle callbacks, a {@code GLSurfaceView} owns the render thread - and neither exists here, so this class owns
 * the loop that turns forwarded events back into a lifecycle on one thread with the EGL context current.
 */
public class BridgeApplication implements Application {
    private static final long SURFACE_TIMEOUT_MILLIS = 20000;

    public final BridgeGraphics graphics;
    public final BridgeInput input;
    public final BridgeFiles files;
    public final BridgeEvents events;

    protected final Seq<ApplicationListener> listeners = new Seq<>();
    protected final Seq<Runnable> runnables = new Seq<>();
    private final Seq<Runnable> executedRunnables = new Seq<>();

    private Thread mainThread;

    public BridgeApplication() {
        this.graphics = new BridgeGraphics();
        this.input = new BridgeInput();
        this.files = new BridgeFiles();
        this.events = new BridgeEvents(graphics, input, listeners);
    }

    /** Runs the game until it exits, blocking the calling thread, which must be the JVM main thread. LWJGL has to be
     *  pointed at the right EGL and GLES libraries before anything asks for a context. */
    public void run() {
        mainThread = Thread.currentThread();

        Core.app = this;
        Core.graphics = graphics;
        Core.input = input;
        Core.files = files;
        Core.settings = new Settings();
        Core.audio = new Audio(true);

        graphics.configure();

        if (!waitForWindow()) {
            Log.error("no surface arrived within " + SURFACE_TIMEOUT_MILLIS + "ms; the game cannot start");
            return;
        }

        if (!graphics.createSurface(Surface.window(), Surface.width(), Surface.height())) {
            Log.error("failed to make the EGL context current; the game cannot start");
            return;
        }
        events.markSurfaceReady();

        for (ApplicationListener listener : listeners)
            listener.init();
        for (ApplicationListener listener : listeners)
            listener.resize(graphics.getWidth(), graphics.getHeight());

        loop();

        teardown();
    }

    /** Waits for ART to hand over the native window, taking events while waiting: ART creates the surface once its
     *  main thread is free again, so the window pointer arrives as one of those events. */
    private boolean waitForWindow() {
        long deadline = System.currentTimeMillis() + SURFACE_TIMEOUT_MILLIS;
        while (!Surface.ready()) {
            JvmBus.pump();
            if (Surface.ready())
                return true;
            if (System.currentTimeMillis() >= deadline)
                return false;
            Threads.sleep(2);
        }
        return true;
    }

    private void loop() {
        while (events.running()) {
            JvmBus.pump();
            runPosted();

            if (events.paused() || !events.surfaceReady()) {
                // nothing may be drawn without a current surface, but events must keep flowing
                Threads.sleep(4);
                continue;
            }

            graphics.beginFrame();
            input.processEvents();
            defaultUpdate();
            for (ApplicationListener listener : listeners)
                listener.update();
            input.processDevices();
            graphics.swapBuffers();
        }
    }

    private void teardown() {
        for (ApplicationListener listener : listeners) {
            try {
                listener.pause();
            } catch (Throwable t) {
                Log.error(t);
            }
        }
        for (ApplicationListener listener : listeners) {
            try {
                listener.exit();
                listener.dispose();
            } catch (Throwable t) {
                // a failing dispose must not stop the rest of the shutdown
                Log.error(t);
            }
        }

        dispose();
        graphics.disposeContext();
        Log.info("game loop ended");
    }

    private void runPosted() {
        synchronized (runnables) {
            executedRunnables.clear();
            executedRunnables.addAll(runnables);
            runnables.clear();
        }
        for (int i = 0; i < executedRunnables.size; i++) {
            try {
                executedRunnables.get(i).run();
            } catch (Throwable t) {
                Log.error(t);
            }
        }
    }

    @Override
    public Seq<ApplicationListener> getListeners() {
        return listeners;
    }

    @Override
    public ApplicationType getType() {
        return ApplicationType.android;
    }

    /** The thread every listener callback arrives on, the one that ran {@link #run()} - and therefore why nothing in
     *  this class is synchronized. */
    @Override
    public Thread getMainThread() {
        return mainThread;
    }

    @Override
    public int getVersion() {
        return JvmCall.getOsVersion();
    }

    @Override
    public long getNativeHeap() {
        return JvmCall.getNativeHeap();
    }

    @Override
    public String getClipboardText() {
        return JvmCall.getClipboardText();
    }

    @Override
    public void setClipboardText(String text) {
        JvmCall.setClipboardText(text);
    }

    @Override
    public boolean openURI(String uri) {
        return JvmCall.openUri(uri);
    }

    @Override
    public boolean openFolder(String folder) {
        return JvmCall.openFolder(folder);
    }

    /** Queues work for the loop thread, the only thread allowed to touch the game. */
    @Override
    public void post(Runnable runnable) {
        synchronized (runnables) {
            runnables.add(runnable);
        }
    }

    @Override
    public void exit() {
        JvmCall.finishActivity();
        events.destroy();
    }
}
