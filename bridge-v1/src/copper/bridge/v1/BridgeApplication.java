package copper.bridge.v1;

import io.anuke.arc.*;
import io.anuke.arc.collection.*;

import copper.bridge.*;
import copper.bridge.gen.*;
import copper.bridge.jvm.Surface;
import copper.bridge.util.*;

import java.util.*;

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

    protected final Array<ApplicationListener> listeners = new Array<>();

    /** Plain lists: Array.addAll and Array.clear return void on the older end of the range. */
    private final List<Runnable> runnables = new ArrayList<>();
    private final List<Runnable> executedRunnables = new ArrayList<>();

    /** Set once the first frame was handed to the surface: the ART side takes down whatever it showed until then. */
    private boolean firstFrameMarked;

    public BridgeApplication() {
        this.graphics = new BridgeGraphics();
        this.input = new BridgeInput();
        this.files = new BridgeFiles();
        this.events = new BridgeEvents(graphics, input, listeners);
    }

    /** Runs the game until it exits, blocking the calling thread, which must be the JVM main thread. LWJGL has to be
     *  pointed at the right EGL and GLES libraries before anything asks for a context. */
    public void run() {
        Core.app = this;
        Core.graphics = graphics;
        Core.input = input;
        Core.files = files;
        Core.settings = new Settings();
        initAudio();
        Core.net = new Net();

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
            sleep(2);
        }
        return true;
    }

    private void loop() {
        while (events.running()) {
            JvmBus.pump();
            runPosted();

            if (events.paused() || !events.surfaceReady()) {
                // nothing may be drawn without a current surface, but events must keep flowing
                sleep(4);
                continue;
            }

            graphics.beginFrame();
            input.processEvents();
            // Application.defaultUpdate does this; autosave() arrived later in the range
            autosaveSettings();
            io.anuke.arc.util.Time.updateGlobal();
            for (ApplicationListener listener : listeners)
                listener.update();
            input.processDevices();
            graphics.swapBuffers();

            if (!firstFrameMarked) {
                firstFrameMarked = true;
                // the ART side owns whatever it shows over the surface, and this is the only thing it waits for
                JvmCall.onFirstFrameDrawn();
            }
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
        for (Runnable executedRunnable : executedRunnables) {
            try {
                executedRunnable.run();
            } catch (Throwable t) {
                Log.error(t);
            }
        }
    }

    /** {@code Settings.autosave} is absent at the range's floor; there is nothing to drive there. */
    private static final java.lang.reflect.Method AUTOSAVE = autosaveMethod();

    private static java.lang.reflect.Method autosaveMethod() {
        try {
            return Settings.class.getMethod("autosave");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void autosaveSettings() {
        if (AUTOSAVE == null)
            return;
        try {
            AUTOSAVE.invoke(Core.settings);
        } catch (Throwable ignored) {
        }
    }

    /** {@code Thread.sleep} with arc's old semantics: an interrupt is swallowed, not re-armed. */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Installs the audio implementation the running game declares: the abstract arc.Audio this branch compiles
     * against, or the SoloudAudio the 11234 build has under its SoLoud name. No mock - a silent game would hide the
     * failure it stands for.
     */
    private static void initAudio() {
        io.anuke.arc.Audio audio;
        try {
            audio = (io.anuke.arc.Audio) Class.forName("io.anuke.arc.audio.SoloudAudio").getConstructor().newInstance();
        } catch (Throwable soloud) {
            try {
                audio = new LegacyAudio();
            } catch (Throwable legacy) {
                Log.error("audio", legacy);
                return;
            }
        }

        Core.audio = audio;
    }

    @Override
    public Array<ApplicationListener> getListeners() {
        return listeners;
    }

    @Override
    public ApplicationType getType() {
        return ApplicationType.Android;
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

    /** Not on the interface at the range's floor, so no {@code @Override}; the game asks for it where it exists. */
    public boolean openURI(String uri) {
        return JvmCall.openUri(uri);
    }

    /** Not on the interface at the range's floor, so no {@code @Override}. */
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
        events.onDestroy();
    }
}