package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import copper.bridge.annotation.CompatRange;
import copper.bridge.gen.JvmCall;
import copper.bridge.util.Log;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.*;
import io.anuke.kryonet.DefaultThreadImpl;
import io.anuke.mindustry.core.*;
import io.anuke.mindustry.core.ThreadHandler.ThreadProvider;
import io.anuke.ucore.function.*;
import io.anuke.ucore.scene.*;
import io.anuke.ucore.scene.event.*;
import io.anuke.ucore.scene.ui.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The game's platform hooks for 40..57. The desktop launcher owns a window and Discord; the bridge owns neither,
 * so the answers that matter are the Android ones: landscape control and the file picker.
 */
@CompatRange(floor = "v57", ceiling = "v57")
public class BridgePlatform extends Platform {
    private final DateFormat dates = SimpleDateFormat.getDateTimeInstance();

    @Override
    @BridgeOverride
    public String format(Date date) {
        return dates.format(date);
    }

    @Override
    @BridgeOverride
    public String format(int number) {
        return NumberFormat.getIntegerInstance().format(number);
    }

    @Override
    @BridgeOverride
    public void addDialog(TextField field, int maxLength) {
        // The game calls this once to attach the tap behaviour; showing the dialog here would open it on start-up.
        ((Element)field).addListener(new ClickListener() {
            @Override
            @BridgeOverride
            public void clicked(InputEvent event, float x, float y) {
                if (Gdx.app.getType() != Application.ApplicationType.Android)
                    return;
                Gdx.input.setOnscreenKeyboardVisible(false);
                JvmCall.textInput("", "", field.getText(), false, false, maxLength, true)
                        .onResult(text -> {
                            field.clearText();
                            field.appendText(text);
                            ((Element)field).fire(new ChangeListener.ChangeEvent());
                            Gdx.graphics.requestRendering();
                        });
            }
        });
    }

    @Override
    @BridgeOverride
    public String getLocaleName(Locale locale) {
        return locale.getDisplayName(locale);
    }

    @Override
    @BridgeOverride
    public ThreadProvider getThreadProvider() {
        return new DefaultThreadImpl();
    }

    @Override
    @BridgeOverride
    public void showFileChooser(String text, String content, Consumer<FileHandle> cons, boolean open,
                                String filetype) {
        String[] extensions = filetype == null || filetype.isEmpty() ? new String[] {""} : new String[] {filetype};
        JvmCall.showFileChooser(open, false, text, null, extensions)
                .onResult(paths -> {
                    for (String path : paths) {
                        if (path != null && !path.isEmpty())
                            cons.accept(open ? Gdx.files.absolute(path)
                                    : new BridgeUriFileHandle(path, filetype == null || filetype.isEmpty()
                                    ? "export" : "export." + filetype));
                    }
                })
                .onError(message -> Log.error("file chooser failed: " + message));
    }

    @Override
    @BridgeOverride
    public void beginForceLandscape() {
        JvmCall.forceLandscape(true);
    }

    @Override
    @BridgeOverride
    public void endForceLandscape() {
        JvmCall.forceLandscape(false);
    }
}
