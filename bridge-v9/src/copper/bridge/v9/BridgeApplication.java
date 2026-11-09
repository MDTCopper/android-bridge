package copper.bridge.v9;

import arc.*;
import arc.audio.*;
import arc.struct.*;
import arc.util.Threads;


import copper.bridge.*;
import copper.bridge.backend.GameLoop;
import copper.bridge.gen.*;
import copper.bridge.v8.BridgeInput;
import copper.bridge.v8.BridgeEvents;
import copper.bridge.v6.BridgeFiles;
import copper.bridge.jvm.Surface;
import copper.bridge.util.*;

/**
 * arc's application object for the JVM side of the bridge. On Android arc is driven by ART - the activity owns the
 * lifecycle callbacks, a {@code GLSurfaceView} owns the render thread - and neither exists here, so this class owns
 * the loop that turns forwarded events back into a lifecycle on one thread with the EGL context current.
 */
public class BridgeApplication implements Application, GameLoop.Host {

    public final BridgeGraphics graphics;
    public final BridgeInput input;
    public final BridgeFiles files;
    public final BridgeEvents events;

    protected final Seq<ApplicationListener> listeners = new Seq<>();
    private final GameLoop gameLoop = new GameLoop();

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

        if (!gameLoop.waitForWindow()) {
            Log.error("no surface arrived within " + GameLoop.SURFACE_TIMEOUT_MILLIS + "ms; the game cannot start");
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

        gameLoop.run(this);

        teardown();
    }



    @Override
    public boolean running() {
        return events.running();
    }
    @Override
    public boolean canDraw() {
        return !events.paused() && events.surfaceReady();
    }


    @Override
    public void frame() {
        graphics.beginFrame();
        input.processEvents();
        defaultUpdate();
        for (ApplicationListener listener : listeners)
            listener.update();
        input.processDevices();
        graphics.swapBuffers();
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
        gameLoop.post(runnable);
    }

    @Override
    public void exit() {
        JvmCall.finishActivity();
        events.onDestroy();
    }
}
