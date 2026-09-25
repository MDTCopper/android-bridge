package copper.bridge.art;

import android.content.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;

import copper.bridge.annotation.*;

/**
 * The read-only facts about Android and about this process: the JVM side has no Android types at
 * all, so the API level, the display rotation and the native heap can only be answered here.
 */
public class OsInfo {
    private final Context context;

    public OsInfo(Context context) {
        this.context = context;
    }

    /** The Android API level, which the game reports and uses for behaviour switches. */
    @ArtDirectHandler
    public int getOsVersion() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * The display's rotation in degrees, as arc's {@code Input.getRotation()} wants it rather than as
     * Android's four rotation constants: the translation happens here because the JVM side has no
     * Android types to translate with.
     */
    @ArtDirectHandler
    @SuppressWarnings("deprecation")
    public int screenRotationDegrees() {
        Display display = display();
        if (display == null)
            return 0;
        switch (display.getRotation()) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    /**
     * Whether the display is natively landscape: the rotation decides whether the reported size is
     * read as-is or swapped, and the wider of the two is the native orientation, so the answer does
     * not depend on how the device is being held.
     */
    @ArtDirectHandler
    @SuppressWarnings("deprecation")
    public boolean isNativeLandscape() {
        Display display = display();
        if (display == null)
            return false;

        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        int rotation = display.getRotation();

        return ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
                        && metrics.widthPixels >= metrics.heightPixels)
                || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
                        && metrics.widthPixels <= metrics.heightPixels);
    }

    /** The display this app is on, or {@code null} when the window manager is not up yet. */
    @SuppressWarnings("deprecation")
    private Display display() {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return manager == null ? null : manager.getDefaultDisplay();
    }

    /** The native heap size, shown in the game's own memory display. */
    @ArtDirectHandler
    public long getNativeHeap() {
        return Debug.getNativeHeapAllocatedSize();
    }
}
