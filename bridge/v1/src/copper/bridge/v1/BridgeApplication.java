package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import copper.bridge.*;
import copper.bridge.gen.JvmBus;
import copper.bridge.gen.JvmCall;
import copper.bridge.util.*;

import com.badlogic.gdx.*;
import com.badlogic.gdx.utils.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * libGDX's application for the JVM side. The activity owns the lifecycle and the GL surface; this class owns the
 * one thread that turns forwarded events into the calls the game expects, with the EGL context current.
 */
public class BridgeApplication implements Application {
    private static final long SURFACE_TIMEOUT_MILLIS = 20000;

    private final ApplicationListener listener;
    private final BridgeGraphics graphics;
    private final BridgeInput input;
    private final BridgeFiles files;
    private final BridgeAudio audio = new BridgeAudio();
    private final BridgeNet net = new BridgeNet();
    private final BridgeEvents events;

    private final List<Runnable> runnables = new ArrayList<>();
    private final List<Runnable> executedRunnables = new ArrayList<>();
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Preferences> preferences = new HashMap<>();

    private ApplicationLogger logger = new BridgeLogger();
    private final Clipboard clipboard = new BridgeClipboard();

    private Thread mainThread;
    private int logLevel = LOG_INFO;
    private boolean firstFrameMarked;

    public BridgeApplication(ApplicationListener listener) {
        this.listener = listener;
        this.graphics = new BridgeGraphics();
        this.input = new BridgeInput();
        File data = Bridge.options.gameDataFolder;
        this.files = new BridgeFiles(data == null ? new File(".") : data);
        this.events = new BridgeEvents(this, graphics, input, listener);
    }

    /** Runs the game until it exits, blocking the calling thread, which must be the JVM main thread. */
    public void run() {
        mainThread = Thread.currentThread();

        Gdx.app = this;
        Gdx.graphics = graphics;
        Gdx.input = input;
        Gdx.files = files;
        Gdx.audio = audio;
        Gdx.net = net;

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

        listener.create();
        listener.resize(graphics.getWidth(), graphics.getHeight());

        loop();

        teardown();
    }

    /** Waits for ART to hand over the native window, taking events while waiting. */
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
                sleep(4);
                continue;
            }

            graphics.beginFrame();
            input.processEvents();
            listener.render();
            graphics.swapBuffers();

            if (!firstFrameMarked) {
                firstFrameMarked = true;
                JvmCall.onFirstFrameDrawn();
            }
        }
    }

    private void teardown() {
        try {
            listener.pause();
        } catch (Throwable t) {
            Log.error(t);
        }
        for (LifecycleListener lifecycle : lifecycleListeners) {
            try {
                lifecycle.dispose();
            } catch (Throwable t) {
                Log.error(t);
            }
        }
        try {
            listener.dispose();
        } catch (Throwable t) {
            Log.error(t);
        }

        graphics.disposeContext();
        Log.info("game loop ended");
    }

    private void runPosted() {
        synchronized (runnables) {
            executedRunnables.clear();
            executedRunnables.addAll(runnables);
            runnables.clear();
        }
        for (Runnable runnable : executedRunnables) {
            try {
                runnable.run();
            } catch (Throwable t) {
                Log.error(t);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    @BridgeOverride
    public ApplicationListener getApplicationListener() {
        return listener;
    }

    @Override
    @BridgeOverride
    public Graphics getGraphics() {
        return graphics;
    }

    @Override
    @BridgeOverride
    public Audio getAudio() {
        return audio;
    }

    @Override
    @BridgeOverride
    public Input getInput() {
        return input;
    }

    @Override
    @BridgeOverride
    public Files getFiles() {
        return files;
    }

    @Override
    @BridgeOverride
    public Net getNet() {
        return net;
    }

    @Override
    @BridgeOverride
    public void log(String tag, String message) {
        logger.log(tag, message);
    }

    @Override
    @BridgeOverride
    public void log(String tag, String message, Throwable exception) {
        logger.log(tag, message, exception);
    }

    @Override
    @BridgeOverride
    public void error(String tag, String message) {
        logger.error(tag, message);
    }

    @Override
    @BridgeOverride
    public void error(String tag, String message, Throwable exception) {
        logger.error(tag, message, exception);
    }

    @Override
    @BridgeOverride
    public void debug(String tag, String message) {
        logger.debug(tag, message);
    }

    @Override
    @BridgeOverride
    public void debug(String tag, String message, Throwable exception) {
        logger.debug(tag, message, exception);
    }

    @Override
    @BridgeOverride
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    @BridgeOverride
    public int getLogLevel() {
        return logLevel;
    }

    @Override
    @BridgeOverride
    public void setApplicationLogger(ApplicationLogger applicationLogger) {
        this.logger = applicationLogger;
    }

    @Override
    @BridgeOverride
    public ApplicationLogger getApplicationLogger() {
        return logger;
    }

    @Override
    @BridgeOverride
    public ApplicationType getType() {
        return ApplicationType.Android;
    }

    @Override
    @BridgeOverride
    public int getVersion() {
        return JvmCall.getOsVersion();
    }

    @Override
    @BridgeOverride
    public long getJavaHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Override
    @BridgeOverride
    public long getNativeHeap() {
        return JvmCall.getNativeHeap();
    }

    @Override
    @BridgeOverride
    public Preferences getPreferences(String name) {
        synchronized (preferences) {
            // The desktop game's own layout: one XML properties file per name under .prefs/.
            return preferences.computeIfAbsent(name, key -> new BridgePreferences(files.local(".prefs/" + key)));
        }
    }

    @Override
    @BridgeOverride
    public Clipboard getClipboard() {
        return clipboard;
    }

    @Override
    @BridgeOverride
    public void postRunnable(Runnable runnable) {
        synchronized (runnables) {
            runnables.add(runnable);
        }
    }

    @Override
    @BridgeOverride
    public void exit() {
        JvmCall.finishActivity();
        events.onDestroy();
    }

    @Override
    @BridgeOverride
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    @Override
    @BridgeOverride
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

}
