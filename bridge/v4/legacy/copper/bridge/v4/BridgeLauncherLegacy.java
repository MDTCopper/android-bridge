package copper.bridge.v4;

import copper.bridge.annotation.BridgeHandled;
import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.*;
import io.anuke.arc.files.*;
import io.anuke.arc.func.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.game.*;
import io.anuke.mindustry.net.*;

import copper.bridge.gen.*;

/** The 5695 Platform shape: the file chooser takes a Cons and an extension string. */
@CompatRange(floor = "5695", ceiling = "5695")
@BridgeHandled(targets = "io/anuke/mindustry/core/Platform:showFileChooser",
        warning = BridgeHandled.Warning.METHOD_SIGNATURE_CHANGED_WITHIN_WINDOW,
        reason = "this legacy launcher implements the Cons overload alongside the Consumer one")
public class BridgeLauncherLegacy extends BridgeLauncher {

    @BridgeOverride
    public void showFileChooser(boolean open, String extension, Cons<FileHandle> cons) {
        String filter = extension == null ? "" : extension;
        JvmCall.showFileChooser(open, false, "", null, new String[] {filter})
                .onResult(paths -> {
                    if (paths == null)
                        return;
                    for (String path : paths) {
                        if (path == null || path.isEmpty())
                            continue;
                        cons.get(open ? Core.files.absolute(path) : new UriFi(path, "export." + filter));
                    }
                })
                .onError(message -> Log.err("file chooser failed: " + message));
    }

    @Override
    protected void installEvents() {
        Events.on(EventType.ClientLoadEvent.class, (Cons<EventType.ClientLoadEvent>)e -> {
            if (!Core.settings.getBool("landscape"))
                endForceLandscape();
        });
    }

    @Override
    protected void sendCrash(Throwable cause) {
        CrashSender.send(cause, (Cons<java.io.File>)file -> {
        });
    }
}
