package copper.bridge.art;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import copper.bridge.*;
import copper.bridge.annotation.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;

/**
 * The activity the launcher returns to the system.
 *
 * <p>It owns the surface, the input path and its own lifecycle: the JVM side renders on its own thread,
 * so the ART main thread stays free for the message loop. The launcher creates it per launch, so the
 * static hand-over below cannot leak between launches.</p>
 */
public class BridgeActivity extends android.app.Activity {
    private BridgeSurfaceView view;
    private BridgeInput input;
    private ViewGroup root;
    private FileChooser fileChooser;

    public BridgeActivity() {
        // The framework instantiates this activity without arguments, so the launch arguments arrive
        // through the one options instance Main.main filled in; null means something else started it.
        if (Bridge.options == null)
            throw new RuntimeException("BridgeActivity was created before copper.bridge.art.Main.main");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        // Earliest point the activity exists, and before any surface, so native has the instances before
        // the JVM side can ask. One bind per class; the batch half is bound where its schema is built.
        ArtBus.bind(this);
        ArtBus.bind(new Vibration(this));
        ArtBus.bind(new Clipboard(this));
        ArtBus.bind(new OsInfo(this));
        ArtBus.bind(new UriLauncher(this));
        ArtBus.bind(new UriFiles(this));
        ArtBus.bind(new TextInput(this));
        ArtBus.bind(fileChooser = new FileChooser(this));

        if (Bridge.options.debug)
            Log.info("activity attached");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideStatusBar();

        root = new FrameLayout(this);
        setContentView(root);

        // Must happen here: the JVM side waits for the window pointer rather than asking for it, so a
        // surface left to a JVM request never arrives and the game waits out its whole timeout. Called
        // directly - through the bus it would queue work for the thread already running.
        surfaceCreate();

        // a staged file from an earlier launch is never needed again
        fileChooser.clearStaged();

        // started before the JVM, so a request during startup is picked up, not left to the first frame
        ArtBus.start();

        // registered before the JVM starts, so a back that arrives during startup is not lost
        registerBack();

        // arc reads its UI scale from the display density. Device read the system resources' value, which
        // is the device's stable density, not necessarily this app's; here this app's own metrics exist.
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        Bridge.options.density = metrics.density;
        Bridge.options.xdpi = metrics.xdpi;
        Bridge.options.ydpi = metrics.ydpi;

        // its own thread: the JVM start blocks until the game exits, so it must not run on the main one
        Thread bootstrap = new Thread(this::startJvm, "copper-bridge-jvm");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    /** Loads the natives, builds the command line and starts the JVM. */
    private void startJvm() {
        try {
            Bootstrap.start();
        } catch (Throwable e) {
            Log.error("failed to start the JVM");
            Log.error(e);
        }
        endProcess();
    }

    /**
     * Ends this process once the JVM is gone. One process can hold one JVM, and Android keeps an empty
     * process around to reuse: a launch that simply returns leaves one that looks alive but can host
     * nothing, so the next launch fails on a second {@code JLI_Launch} or on loading the same library
     * into a fresh class loader. Only this activity is finished, never the host's task.
     */
    private void endProcess() {
        Log.info("the JVM is gone, leaving this screen");
        runOnUiThread(this::leave);
    }

    // requests from the JVM side that need this activity itself: the surface, the keyboard, and the
    // four that change what the activity is doing

    /** The activity this process serves; native reaches it through {@link #endAfterJvmExit}. */
    private static BridgeActivity current;

    /**
     * The return transition: this screen leaves to the right, the host comes back from the left. The
     * platform's own animations, because this bridge is a jar and carries no resources of its own.
     */
    private static final int RETURN_ENTER = android.R.anim.slide_in_left;
    private static final int RETURN_EXIT = android.R.anim.slide_out_right;

    /**
     * Leaves this screen when the VM ends itself. Called from native on the thread the VM was exiting on,
     * at the head of its {@code exit()}: the destructors that follow are what kills this process while
     * ART-side threads are still running, so that thread is held there and this does the ending instead.
     * Static because the caller is a native thread with no activity to hand over.
     */
    @UsedByNative(side = UsedByNative.Side.ART, detaches = true)
    @SuppressWarnings("unused")
    private static void endAfterJvmExit() {
        BridgeActivity activity = current;
        if (activity == null)
            return;
        Log.info("the JVM ended itself: leaving this screen");
        activity.runOnUiThread(activity::leave);
    }

    /**
     * Finishes this screen, so the host below it comes back with the exit below. Only this activity is
     * finished, never the host's task: removing the task would take the host's screen down with it. The
     * process is left to the system to reclaim.
     *
     * <p>The transition is a return rather than a fade. It is set on the newer call from Android 14 on,
     * because the platform ignores the older one for an app that targets it - which is what made this
     * screen disappear without any transition at all; below that, the older call is the one that works.</p>
     */
    private void leave() {
        if (isFinishing())
            return;

        Log.info("leaving this screen");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, RETURN_ENTER, RETURN_EXIT);
        else
            legacyTransition();
        finish();
    }

    /**
     * The call Android 14 replaced: below API 34 the platform ignores {@code overrideActivityTransition}
     * and honours only this one, so it stays for exactly those levels.
     */
    @SuppressWarnings("deprecation")
    private void legacyTransition() {
        overridePendingTransition(RETURN_ENTER, RETURN_EXIT);
    }

    /** Creates the render surface. Runs on the ART main thread. */
    @ArtPostHandler
    public boolean surfaceCreate() {
        runOnUiThread(() -> {
            if (view != null)
                return;
            view = new BridgeSurfaceView(this);
            view.getHolder().addCallback(new SurfaceHolderCallback());
            input = new BridgeInput(view);
            root.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        });
        return true;
    }

    /** Tears the render surface down again. */
    @ArtPostHandler
    public boolean surfaceDestroy() {
        runOnUiThread(() -> {
            if (view == null)
                return;
            root.removeView(view);
            view = null;
            input = null;
        });
        return true;
    }

    /** Moves the game to the background. */
    @ArtPostHandler
    public void hide() {
        runOnUiThread(() -> moveTaskToBack(true));
    }

    /**
     * Ends the game, the same way every other ending does: through {@link #leave} rather than finishing
     * here, because the exit transition has to be set before the finish.
     */
    @ArtPostHandler
    public void finishActivity() {
        Log.info("the game asked to finish");
        runOnUiThread(this::leave);
    }

    /** Asks the system for runtime permissions on behalf of the game. */
    @ArtPostHandler
    public void permissions(int code, String[] permissions) {
        runOnUiThread(() -> requestPermissions(permissions, code));
    }

    /**
     * Pins the activity to landscape, or releases it back to the user's rotation preference. The host
     * declares the activity landscape and only this call can override that while the task runs; these are
     * the two answers the game's own Android launcher gives, so a released game follows the user's
     * rotation setting and can end up portrait, which is what the game asks for by name.
     */
    @ArtPostHandler
    public void orientation(boolean force) {
        setRequestedOrientation(force
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_USER);
    }

    /**
     * Raises or lowers the system keyboard for the game. Runs on the main thread, which is the only
     * thread that may touch an input method; the surface view holds the game's focus, and the keys come
     * back through this activity, which forwards key events to the game.
     */
    @ArtPostHandler
    public void onscreenKeyboard(boolean visible) {
        if (view == null) {
            // There is nothing to attach a keyboard to yet. The game asks again when a text field
            // takes focus, so a request that arrives before the surface exists is dropped.
            Log.warn("the onscreen keyboard was requested before the surface existed");
            return;
        }

        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager == null)
            return;

        if (visible) {
            // The view has to be the focused one, or the system has no window to show the keyboard
            // in; a touch already focuses it, but a field the game focuses by itself does not.
            view.requestFocus();
            manager.showSoftInput(view, 0);
        } else {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /** Converts an Android {@code Surface} into an {@code ANativeWindow*} and returns it. */
    @Native("jni::NativeWindow")
    private native long nativeWindow(Object surface);

    // surface callbacks

    private class SurfaceHolderCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            long window = nativeWindow(holder.getSurface());
            if (Bridge.options.debug)
                Log.info("surface created, window=0x" + Long.toHexString(window));
            ArtCall.surfaceCreated(view.getWidth(), view.getHeight(), window);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            ArtCall.surfaceResized(width, height);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            ArtCall.surfaceDestroyed();
        }
    }

    // lifecycle

    /**
     * The window reaches the window manager only after {@link #onResume} returns, so both hides before
     * this point - {@link #onCreate} and {@link #onResume} - find a decor that has no insets controller
     * yet. Here the decor is attached and has one, which is what hides the bars from the first frame
     * instead of from the first focus change.
     */
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        hideStatusBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ArtCall.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideStatusBar();
        ArtCall.resume();
    }

    @Override
    protected void onDestroy() {
        // The pump is stopped first: it runs on this thread, and a request performed against a destroyed
        // activity would only throw. Stopping it also releases a caller still waiting for an answer.
        ArtBus.stop();
        super.onDestroy();
        ArtCall.destroy();
        current = null;
    }

    /**
     * The picker's answer, the only activity result this bridge asks for. Forwarded rather than
     * interpreted: the request code it packed is the whole of what it needs back.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        fileChooser.activityResult(requestCode, resultCode, data);
    }

    // input

    /**
     * Forwards a key press. Repeated {@code ACTION_DOWN} events are dropped here as well as on the JVM
     * side, because a held back key otherwise reaches the game as a stream of presses - which is what
     * made the launcher this bridge replaces re-trigger its back handling in a loop.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0)
            return consumes(keyCode);
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Reported, not forwarded as a key. Below API 33 this is how a three button back arrives; from
            // 33 on the dispatcher callback does it. Either way the JVM side decides, and it can only do
            // that if the press never touches the keyboard device's per-frame state.
            pressBack();
            return true;
        }
        return input != null && input.keyDown(keyCode, event.getRepeatCount());
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // the press was already reported on the way down
        if (keyCode == KeyEvent.KEYCODE_BACK)
            return true;
        return input != null && input.keyUp(keyCode, BridgeInput.characters(event));
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        // long press is a launcher concern, not a game input
        return true;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return input != null && input.text(BridgeInput.characters(event));
    }

    /**
     * Back on devices older than API 33, where it is the only entry point: from 33 on the system's
     * dispatcher owns back and the window never sees a key event for it, so this is not called there.
     * Both entry points run the same {@link #pressBack}.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Below API 33 only. From 33 on, registering the dispatcher callback does not stop the framework
        // from reaching this method for the same press as well, and running pressBack twice delivered back
        // twice: the game closed its dialog on the first one and saw a second with no dialog left.
        if (android.os.Build.VERSION.SDK_INT < 33)
            pressBack();
    }

    /**
     * Registers for the system's back dispatcher, which is how back arrives from API 33 on: both a three
     * button back and an edge swipe are delivered as a back invocation, never as a key event the window
     * could see. Without it the game would simply never be told about back.
     */
    private void registerBack() {
        if (android.os.Build.VERSION.SDK_INT < 33)
            return;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::pressBack);
    }

    /** One back press, reported to the JVM side, which is the side that knows what back means. */
    private void pressBack() {
        ArtCall.back();
    }

    /** Whether a key is one the game wants and the activity should therefore consume. */
    private boolean consumes(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_MENU;
    }

    /**
     * Hides both system bars the way the game's own Android launcher does through arc's
     * {@code useImmersiveMode}: the controller below is the modern half of it, and the flags it also
     * sets are the other. The back key is why the bars cannot simply be left alone: with them hidden, a
     * three-button device spends the first press on revealing them, so the game sees no back at all
     * (measured). The behaviour set below is what hides transient bars again after a swipe - the job
     * the flags needed a visibility listener for.
     *
     * <p>The controller is asked of the decor view, never of the window: {@code PhoneWindow}'s own
     * {@code getInsetsController()} dereferences the decor it holds, and no decor exists yet at the
     * first call - it is installed by {@code getDecorView()}, which the old flag based code went
     * through, and nothing before {@code setContentView} installs it. The decor in turn answers with no
     * controller at all until it is attached to a window, which happens after {@link #onResume}, so the
     * call made before that is repeated by {@link #onAttachedToWindow} rather than lost.</p>
     */
    private void hideStatusBar() {
        Window window = getWindow();
        layoutUnderBars(window);
        legacyBarFlags();

        WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
        if (controller == null)
            return;
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /**
     * The flags the game's own Android launcher sets, kept beside the controller above.
     *
     * <p>On a stock system the two are the same request: the platform turns {@code HIDE_NAVIGATION}
     * into a hide of the navigation bar types. What the flags carry and the controller does not is the
     * window's own visibility, which the window manager is given with every set of attributes, and a
     * vendor build that decides the navigation bar from that field alone never hides it - measured on
     * one: the controller's request alone left the bar drawn over the game, while the game's own
     * Android build, which sets these flags, had none. Both are kept so the bar is hidden on either
     * kind of system.</p>
     */
    @SuppressWarnings("deprecation")
    private void legacyBarFlags() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /**
     * Lays the content out under the bars, the half of the old flags that was not about hiding them - what
     * keeps the surface from resizing while transient bars are shown. No version gate: API 35 deprecated
     * the call in favour of AndroidX's {@code enableEdgeToEdge}, which this module cannot depend on, and a
     * host that targets below 35 still needs it there.
     */
    @SuppressWarnings("deprecation")
    private void layoutUnderBars(Window window) {
        window.setDecorFitsSystemWindows(false);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            hideStatusBar();
        else if (input != null)
            input.cancelAllPointers();
    }
}
