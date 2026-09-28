package copper.bridge.art;

import android.content.*;
import android.graphics.*;
import android.view.*;

/**
 * The surface the game renders into. A plain {@code SurfaceView} because the bridge uses none of Android's GL
 * helpers: the surface is handed to the JVM side as a native window and EGL is set up there, which keeps the
 * rendering path identical to the desktop one and lets the bridge choose ANGLE or the system GLES libraries.
 */
public class BridgeSurfaceView extends SurfaceView implements View.OnTouchListener {
    public BridgeSurfaceView(Context context) {
        super(context);
        SurfaceHolder holder = getHolder();
        holder.setFormat(PixelFormat.RGBA_8888);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOnTouchListener(this);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (!isFocusableInTouchMode() || !hasFocus()) {
            setFocusableInTouchMode(true);
            requestFocus();
        }
        return false;
    }
}
