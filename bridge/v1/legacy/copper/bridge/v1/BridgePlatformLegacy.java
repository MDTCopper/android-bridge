package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import copper.bridge.annotation.CompatRange;
import copper.bridge.gen.JvmCall;

import com.badlogic.gdx.*;

import io.anuke.kryonet.DefaultThreadImpl;
import io.anuke.mindustry.core.ThreadHandler.ThreadProvider;
import io.anuke.mindustry.io.Platform;
import io.anuke.ucore.scene.*;
import io.anuke.ucore.scene.event.*;
import io.anuke.ucore.scene.ui.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * The platform hooks for 33..39, where the type still lives in io.anuke.mindustry.io. It is compiled against the
 * floor jar because the main sources are compiled against 57, where that package no longer exists.
 */
@CompatRange(floor = "v33", ceiling = "v33")
public class BridgePlatformLegacy extends Platform {
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
    public boolean hasDiscord() {
        return false;
    }

    @Override
    @BridgeOverride
    public boolean isDebug() {
        return false;
    }

    @Override
    @BridgeOverride
    public byte[] getUUID() {
        byte[] result = new byte[8];
        new Random().nextBytes(result);
        return result;
    }

    @Override
    @BridgeOverride
    public ThreadProvider getThreadProvider() {
        return new DefaultThreadImpl();
    }
}
