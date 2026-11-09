package copper.bridge.v7;

import arc.*;
import arc.struct.*;

import copper.bridge.*;
import copper.bridge.backend.GameLoop;
import copper.bridge.gen.*;
import copper.bridge.v6.BridgeGraphics;
import copper.bridge.v6.BridgeInput;
import copper.bridge.v6.BridgeEvents;
import copper.bridge.v6.BridgeFiles;
import copper.bridge.v6.LegacyAudio;
import copper.bridge.jvm.Surface;
import copper.bridge.util.*;

import java.util.*;

/**
 * arc's application object for the JVM side of the bridge. On Android arc is driven by ART - the activity owns the
 * lifecycle callbacks, a {@code GLSurfaceView} owns the render thread - and neither exists here, so this class owns
 * the loop that turns forwarded events back into a lifecycle on one thread with the EGL context current.
 */
public class BridgeApplication implements Application, GameLoop.Host {

    /** {@code android}, by ordinal: the constant is capitalized on the range's older end. */
    public static final ApplicationType ANDROID = ApplicationType.values()[0];

    public final BridgeGraphics graphics;
    public final BridgeInput input;
    public final BridgeFiles files;
    public final BridgeEvents events;

    protected final Seq<ApplicationListener> listeners = new Seq<>();

    private final GameLoop gameLoop = new GameLoop();


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
        // Application.defaultUpdate does this; the method itself arrived after the floor, so spell it out
        Core.settings.autosave();
        arc.util.Time.updateGlobal();
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
        arc.Audio audio;
        try {
            audio = (arc.Audio) Class.forName("arc.audio.SoloudAudio").getConstructor().newInstance();
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
    public Seq<ApplicationListener> getListeners() {
        return listeners;
    }

    @Override
    public ApplicationType getType() {
        return ANDROID;
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
