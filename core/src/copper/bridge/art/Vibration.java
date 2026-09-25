package copper.bridge.art;

import android.content.*;
import android.content.pm.*;
import android.os.*;

import copper.bridge.annotation.*;
import copper.bridge.util.*;

/**
 * The device's vibrator. {@code android.permission.VIBRATE} is not a runtime permission - the host
 * app either declares it or every call throws - so the check below keeps the game from being told a
 * vibrator is available when using it would only fail.
 */
public class Vibration {
    private final Context context;

    public Vibration(Context context) {
        this.context = context;
    }

    /**
     * The vibrator, or {@code null} when this app may not use one. The permission is checked on the
     * context rather than on the activity, so this class needs nothing an activity owns.
     */
    private Vibrator vibrator() {
        if (context.checkSelfPermission(android.Manifest.permission.VIBRATE)
                != PackageManager.PERMISSION_GRANTED)
            return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            return managerVibrator();
        return legacyVibrator();
    }

    /** The system's default vibrator, the only lookup Android 12 and later offers. */
    private Vibrator managerVibrator() {
        VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
        return manager == null ? null : manager.getDefaultVibrator();
    }

    /**
     * The lookup API 30 forces: there the manager service does not exist yet and
     * {@code VIBRATOR_SERVICE} is still the way to the same vibrator.
     */
    @SuppressWarnings("deprecation")
    private Vibrator legacyVibrator() {
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /** Whether this device has a vibrator the game is allowed to use. */
    @ArtDirectHandler
    public boolean isVibratorAvailable() {
        Vibrator vibrator = vibrator();
        return vibrator != null && vibrator.hasVibrator();
    }

    /**
     * Vibrates for the given number of milliseconds, through {@code VibrationEffect} rather than the
     * plain duration overload, which is deprecated since API 26 - no version guard is needed because
     * this bridge never runs below API 30.
     */
    @ArtDirectHandler
    public void vibrate(int milliseconds) {
        try {
            Vibrator vibrator = vibrator();
            if (vibrator == null)
                return;
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable e) {
            Log.warn("cannot vibrate: " + e);
        }
    }

    /**
     * Vibrates a waveform: the first entry is the wait before the first pulse, the rest alternate
     * between on and off, and {@code repeat} is the index to restart from, or -1 for a single run. A
     * null pattern means the JVM side could not hand the array over, so stopping is the only answer.
     */
    @ArtDirectHandler
    public void vibrate(long[] pattern, int repeat) {
        try {
            Vibrator vibrator = vibrator();
            if (vibrator == null)
                return;
            if (pattern == null || pattern.length == 0) {
                vibrator.cancel();
                return;
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
        } catch (Throwable e) {
            Log.warn("cannot vibrate the requested pattern: " + e);
        }
    }

    /** Stops whatever vibration is running. */
    @ArtDirectHandler
    public void cancelVibrate() {
        try {
            Vibrator vibrator = vibrator();
            if (vibrator != null)
                vibrator.cancel();
        } catch (Throwable e) {
            Log.warn("cannot cancel the vibration: " + e);
        }
    }
}
