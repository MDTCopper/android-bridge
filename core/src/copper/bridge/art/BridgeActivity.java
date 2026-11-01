package copper.bridge.art;

import android.animation.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.animation.*;
import android.view.inputmethod.*;
import android.widget.*;

import copper.bridge.*;
import copper.bridge.annotation.*;
import copper.bridge.gen.*;
import copper.bridge.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * The activity the launcher returns to the system: it owns the surface, the input path and its own lifecycle.
 */
public class BridgeActivity extends android.app.Activity {
    private BridgeSurfaceView view;
    private BridgeInput input;
    /** Copper defaults: the launcher's dark page and brand color, so the mask needs no handoff. */
    private static final int COPPER_BG = 0xFF121212;
    private static final int COPPER_ACCENT = 0xFFC8A06E;
    private static final long INTERACTION_AFTER_MS = 6000;
    private static final long HINT_VISIBLE_MS = 1000;
    private static final long RING_FULL_MS = 2500;
    private static final long STATUS_AFTER_MS = 3000;

    private ViewGroup root;
    private FileChooser fileChooser;

    /** Black layer over the surface, faded out once the JVM reports the game's first frame. */
    private View veil;
    private TextView statusText;
    private ObjectAnimator breath;
    private ImageView copperIcon;
    private TextView hintText;
    private ProgressRingView ringView;
    private ValueAnimator ringAnimator;
    private Runnable hintTimeout;
    private boolean interactionEnabled;
    private boolean hintVisible;
    private int framePolls;
    private boolean leaving;
    private static final int MAX_FRAME_POLLS = 60;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private final Runnable framePoll = new Runnable() {
        @Override
        public void run() {
            File cache = Bridge.options.cacheFolder;
            File marker = cache == null ? null : new File(cache, "first-frame");
            if (marker == null || marker.isFile() || framePolls++ >= MAX_FRAME_POLLS) {
                if (marker != null)
                    marker.delete();
                fadeVeil();
                return;
            }
            frameHandler.postDelayed(this, 50);
        }
    };

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

        // The game's first frame lands a moment after the surface exists: a copper layer stays
        // until the JVM marks that frame, then fades out so the picture eases in instead of a
        // black gap. Copper colors are the launcher's default palette, independent of its theme.
        File cache = Bridge.options.cacheFolder;
        if (cache != null)
            new File(cache, "first-frame").delete();
        FrameLayout veilLayer = new FrameLayout(this);
        veilLayer.setBackgroundColor(COPPER_BG);
        veilLayer.setAlpha(0f);
        veilLayer.addView(buildVeilContent(),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        ringView = new ProgressRingView(this);
        ringView.setVisibility(View.GONE);
        veilLayer.addView(ringView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Status text waits in the bottom-right corner: only a launch that is still on the copper
        // screen after STATUS_AFTER_MS gets it, and it fades in and then breathes.
        statusText = new TextView(this);
        statusText.setText("正在启动游戏…");
        statusText.setTextSize(14);
        statusText.setTextColor(0xFFF0F0F0);
        statusText.setAlpha(0f);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        statusParams.rightMargin = dp(24);
        statusParams.bottomMargin = dp(24);
        veilLayer.addView(statusText, statusParams);
        frameHandler.postDelayed(() -> {
            if (veil != null && !isFinishing()) {
                statusText.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .withEndAction(this::startBreathing)
                        .start();
            }
        }, STATUS_AFTER_MS);

        // Only a launch still on the copper screen after INTERACTION_AFTER_MS accepts the
        // tap/long-press escape hatch.
        frameHandler.postDelayed(() -> {
            if (veil != null && !isFinishing())
                interactionEnabled = true;
        }, INTERACTION_AFTER_MS);

        View touchLayer = new View(this);
        touchLayer.setClickable(true);
        touchLayer.setOnTouchListener(this::handleVeilTouch);
        veilLayer.addView(touchLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        veil = veilLayer;
        root.addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        veilLayer.animate().alpha(1f).setDuration(200).start();
        frameHandler.post(framePoll);

        fileChooser.clearStaged();

        ArtBus.start();

        registerBack();
        updatePpi();

        Thread bootstrap = new Thread(this::startJvm, "copper-bridge-jvm");
        bootstrap.setDaemon(true);
        bootstrap.start();
    }

    /** Fades the launch veil out and removes it: the game picture is under it. */
    private void fadeVeil() {
        View fading = veil;
        if (fading == null)
            return;
        veil = null;
        stopRing();
        if (hintTimeout != null) {
            frameHandler.removeCallbacks(hintTimeout);
            hintTimeout = null;
        }
        if (breath != null) {
            breath.cancel();
            breath = null;
        }
        fading.animate()
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> root.removeView(fading))
                .start();
    }

    /** Slow breathing of the status text: on-off alpha while the game is still coming up. */
    private void startBreathing() {
        if (statusText == null)
            return;
        breath = ObjectAnimator.ofFloat(statusText, View.ALPHA, 1f, 0.65f);
        breath.setDuration(2400);
        breath.setInterpolator(new AccelerateDecelerateInterpolator());
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.start();
    }

    /** Center: the Copper icon, swapped for the long-press hint on a tap. */
    private View buildVeilContent() {
        FrameLayout center = new FrameLayout(this);

        copperIcon = new ImageView(this);
        copperIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            copperIcon.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName()));
        } catch (Throwable ignored) {
            TextView fallback = new TextView(this);
            fallback.setText("C");
            fallback.setTextSize(48);
            fallback.setTextColor(COPPER_ACCENT);
            center.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        }
        center.addView(copperIcon, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        loadMindustryLogo();

        hintText = new TextView(this);
        hintText.setText("长按任意位置以取消此次启动...");
        hintText.setTextSize(16);
        hintText.setTextColor(0xFFF0F0F0);
        hintText.setGravity(Gravity.CENTER);
        hintText.setAlpha(0f);
        hintText.setVisibility(View.GONE);
        center.addView(hintText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return center;
    }

    /** The Mindustry logo is inside the game jar: icons/icon.png. */
    private void loadMindustryLogo() {
        List<File> jars = Bridge.options.gameJars;
        if (jars == null || jars.isEmpty())
            return;
        new Thread(() -> {
            for (File jar : jars) {
                if (jar == null || !jar.isFile())
                    continue;
                try (ZipFile zip = new ZipFile(jar)) {
                    ZipEntry entry = zip.getEntry("icons/icon.png");
                    if (entry == null)
                        continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        if (bitmap != null) {
                            runOnUiThread(() -> {
                                if (copperIcon != null)
                                    copperIcon.setImageBitmap(bitmap);
                            });
                        }
                    }
                    return;
                } catch (Throwable ignored) {
                }
            }
        }, "copper-logo").start();
    }

    private boolean handleVeilTouch(View v, MotionEvent event) {
        if (veil == null)
            return false;
        if (!interactionEnabled)
            return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                showHint();
                startRing(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stopRing();
                return true;
            default:
                return true;
        }
    }

    private void showHint() {
        if (hintText == null || veil == null)
            return;
        if (!hintVisible) {
            hintVisible = true;
            copperIcon.animate().alpha(0f).setDuration(150).start();
            hintText.setVisibility(View.VISIBLE);
            hintText.setAlpha(0f);
            hintText.animate().alpha(1f).setDuration(150).start();
            frameHandler.postDelayed(this::pulseHint, 160);
        } else {
            pulseHint();
        }
        if (hintTimeout == null)
            hintTimeout = this::hideHint;
        frameHandler.removeCallbacks(hintTimeout);
        frameHandler.postDelayed(hintTimeout, HINT_VISIBLE_MS);
    }

    private void hideHint() {
        if (!hintVisible)
            return;
        hintVisible = false;
        hintText.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (!hintVisible)
                        hintText.setVisibility(View.GONE);
                })
                .start();
        copperIcon.animate().alpha(1f).setDuration(150).start();
    }

    private void pulseHint() {
        if (hintText == null || !hintVisible || veil == null)
            return;
        hintText.animate().cancel();
        hintText.setAlpha(1f);
        hintText.setScaleX(1f);
        hintText.setScaleY(1f);
        hintText.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(160)
                .setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() -> hintText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(new OvershootInterpolator(2f))
                        .start())
                .start();
    }

    private void startRing(float x, float y) {
        if (ringView == null)
            return;
        stopRing();
        ringView.setCenter(x, y);
        ringView.setProgress(0f);
        ringView.setVisibility(View.VISIBLE);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(RING_FULL_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> ringView.setProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (ringAnimator != animation)
                    return;
                ringAnimator = null;
                ringView.setVisibility(View.GONE);
                cancelLaunch();
            }
        });
        ringAnimator = animator;
        animator.start();
    }

    private void stopRing() {
        if (ringAnimator != null) {
            ValueAnimator animator = ringAnimator;
            ringAnimator = null;
            animator.cancel();
        }
        if (ringView != null)
            ringView.setVisibility(View.GONE);
    }

    /** Fills from the touch point; the game is canceled when it completes. */
    private static class ProgressRingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        private float cx;
        private float cy;
        private float progress;

        ProgressRingView(Context context) {
            super(context);
            float density = getResources().getDisplayMetrics().density;
            radius = 36f * density;
            float stroke = 4f * density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(stroke);
            track.setColor(0x33C8A06E);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(stroke);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(0xFFC8A06E);
        }

        void setCenter(float x, float y) {
            cx = x;
            cy = y;
            invalidate();
        }

        void setProgress(float value) {
            progress = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (progress <= 0f)
                return;
            canvas.drawCircle(cx, cy, radius, track);
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                    -90f, 360f * progress, false, arc);
        }
    }

    /** Lets the player stop a launch that is taking too long. */
    private void cancelLaunch() {
        Log.info("launch canceled by user");
        File cache = Bridge.options.cacheFolder;
        if (cache != null) {
            try {
                cache.mkdirs();
                new File(cache, "launch-canceled").createNewFile();
            } catch (Throwable ignored) {
            }
        }
        leave();
        frameHandler.postDelayed(
                () -> android.os.Process.killProcess(android.os.Process.myPid()), 500);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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

    private static final int RETURN_ENTER = android.R.anim.fade_in;
    private static final int RETURN_EXIT = android.R.anim.fade_out;

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

    /** Fades the game picture out, then finishes so the host below comes back. */
    private void leave() {
        if (isFinishing() || leaving)
            return;
        leaving = true;

        Log.info("leaving this screen");
        View fade = new View(this);
        fade.setBackgroundColor(COPPER_BG);
        fade.setAlpha(0f);
        root.addView(fade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fade.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction(() -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, RETURN_ENTER, RETURN_EXIT);
                    else
                        legacyTransition();
                    finish();
                })
                .start();
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
    public void forceLandscape(boolean force) {
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
        frameHandler.removeCallbacks(framePoll);
        if (hintTimeout != null) {
            frameHandler.removeCallbacks(hintTimeout);
            hintTimeout = null;
        }
        stopRing();
        if (breath != null) {
            breath.cancel();
            breath = null;
        }
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

    //region display

    private boolean insetsAttached = false;

    private void updatePpi() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        Bridge.options.density = metrics.density;
        Bridge.options.xdpi = metrics.xdpi;
        Bridge.options.ydpi = metrics.ydpi;
    }

    private void attachSafeInsetsListener() {
        if (insetsAttached)
            return;
        View decorView = getWindow().getDecorView();
        decorView.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
            ArtCall.safeInsetsUpdated(cutout.top, cutout.bottom, cutout.left, cutout.right);
            return insets;
        });
        insetsAttached = true;
        decorView.requestApplyInsets();
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
        boolean forwarded = input != null && input.keyDown(keyCode, event.getRepeatCount());
        return forwarded && !frameworkKey(keyCode);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK)
            return true;
        boolean forwarded = input != null && input.keyUp(keyCode, BridgeInput.characters(event));
        return forwarded && !frameworkKey(keyCode);
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
                || keyCode == KeyEvent.KEYCODE_MENU;
    }

    /**
     * Keys the framework has to keep. Reporting one handled ends it at this activity: an unhandled key is what
     * the system's own volume handling waits for, so a swallowed volume key moves nothing. The game binds none
     * of them, and vanilla arc forwards them the same way - the down travels on, the return value stays false.
     */
    private static boolean frameworkKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
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
        if (hasFocus) {
            hideStatusBar();
            attachSafeInsetsListener();
        } else if (input != null) {
            input.cancelAllPointers();
        }
    }
    //endregion
}
