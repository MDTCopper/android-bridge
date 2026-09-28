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
 * The activity the launcher returns to the system: it owns the surface, the input path and its own lifecycle.
 */
public class BridgeActivity extends android.app.Activity {
    private BridgeSurfaceView view;
    private BridgeInput input;
    private ViewGroup root;
    private FileChooser fileChooser;

    public BridgeActivity() {
        if (Bridge.options == null)
            throw new RuntimeException("BridgeActivity was created before copper.bridge.art.Main.main");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
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

        // must happen here: the JVM waits for the window pointer, so a surface left to a JVM request never arrives
        surfaceCreate();

        fileChooser.clearStaged();

        ArtBus.start();

        registerBack();

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        Bridge.options.density = metrics.density;
        Bridge.options.xdpi = metrics.xdpi;
        Bridge.options.ydpi = metrics.ydpi;

        Thread bootstrap = new Thread(this::startJvm, "copper-bridge-jvm");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    private void startJvm() {
        try {
            Bootstrap.start();
        } catch (Throwable e) {
            Log.error("failed to start the JVM");
            Log.error(e);
        }
        endProcess();
    }

    /** Ends this process once the JVM is gone: Android reuses an empty process, and one process holds one JVM. */
    private void endProcess() {
        Log.info("the JVM is gone, leaving this screen");
        runOnUiThread(this::leave);
    }

    //region requests from the JVM side

    private static BridgeActivity current;

    private static final int RETURN_ENTER = android.R.anim.slide_in_left;
    private static final int RETURN_EXIT = android.R.anim.slide_out_right;

    /** Leaves this screen when the VM ends itself, called from native at the head of its {@code exit()}. */
    @UsedByNative(side = UsedByNative.Side.ART, detaches = true)
    @SuppressWarnings("unused")
    private static void endAfterJvmExit() {
        BridgeActivity activity = current;
        if (activity == null)
            return;
        Log.info("the JVM ended itself: leaving this screen");
        activity.runOnUiThread(activity::leave);
    }

    /** Finishes this screen so the host below comes back; only this activity, never the host's task. */
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

    @SuppressWarnings("deprecation")
    private void legacyTransition() {
        overridePendingTransition(RETURN_ENTER, RETURN_EXIT);
    }

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

    @ArtPostHandler
    public void hide() {
        runOnUiThread(() -> moveTaskToBack(true));
    }

    @ArtPostHandler
    public void finishActivity() {
        Log.info("the game asked to finish");
        runOnUiThread(this::leave);
    }

    @ArtPostHandler
    public void permissions(int code, String[] permissions) {
        runOnUiThread(() -> requestPermissions(permissions, code));
    }

    @ArtPostHandler
    public void orientation(boolean force) {
        setRequestedOrientation(force
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_USER);
    }

    @ArtPostHandler
    public void onscreenKeyboard(boolean visible) {
        if (view == null) {
            Log.warn("the onscreen keyboard was requested before the surface existed");
            return;
        }

        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager == null)
            return;

        if (visible) {
            view.requestFocus();
            manager.showSoftInput(view, 0);
        } else {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Native("jni::NativeWindow")
    private native long nativeWindow(Object surface);
    //endregion

    //region surface callbacks

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
    //endregion

    //region lifecycle

    /** The window reaches the window manager only after {@link #onResume}, so the hides before it find no controller. */
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
        // the pump first: it runs on this thread, and stopping it releases a caller still waiting for an answer
        ArtBus.stop();
        super.onDestroy();
        ArtCall.destroy();
        current = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        fileChooser.activityResult(requestCode, resultCode, data);
    }
    //endregion

    //region input

    /** Forwards a key press. Repeated {@code ACTION_DOWN} events are dropped here as well as on the JVM side. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0)
            return consumes(keyCode);
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // reported, not forwarded: below API 33 a three button back arrives this way, and the JVM decides
            pressBack();
            return true;
        }
        return input != null && input.keyDown(keyCode, event.getRepeatCount());
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            return true;
        return input != null && input.keyUp(keyCode, BridgeInput.characters(event));
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return true;
    }
    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return input != null && input.text(BridgeInput.characters(event));
    }

    /** Back below API 33; the framework reaches this method for the same press, so the check keeps it to one. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (android.os.Build.VERSION.SDK_INT < 33)
            pressBack();
    }

    private void registerBack() {
        if (android.os.Build.VERSION.SDK_INT < 33)
            return;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::pressBack);
    }

    private void pressBack() {
        ArtCall.back();
    }

    private boolean consumes(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_MENU;
    }

    /**
     * Hides both bars the way the game's own launcher does through arc's {@code useImmersiveMode};
     * {@link #legacyBarFlags} is the other half. With them hidden, a three-button device spends the first back press.
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
     * The flags the game's own launcher sets, kept beside the controller above: on a stock system the two are one
     * request, but one vendor build decides the bar from this field alone (measured).
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

    /** Lays the content out under the bars, so the surface does not resize while transient bars show. */
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
    //endregion
}
