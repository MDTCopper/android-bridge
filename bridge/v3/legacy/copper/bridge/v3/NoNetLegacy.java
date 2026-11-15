package copper.bridge.v3;

import copper.bridge.annotation.BridgeHandled;
import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.*;
import io.anuke.arc.func.*;

import copper.bridge.gen.*;

/** The 5695 Net shape: http takes Cons callbacks. */
@CompatRange(floor = "5695", ceiling = "5695")
@BridgeHandled(targets = "io/anuke/arc/Net:http",
        warning = BridgeHandled.Warning.METHOD_SIGNATURE_CHANGED_WITHIN_WINDOW,
        reason = "this legacy Net implements the Cons http signature")
public class NoNetLegacy implements Net {

    @BridgeOverride
    public void http(HttpRequest request, Cons<HttpResponse> success, Cons<Throwable> failure) {
        if (failure != null)
            failure.get(new IllegalStateException("no HTTP stack on the bridge"));
    }

    @BridgeOverride
    public boolean openURI(String uri) {
        return JvmCall.openUri(uri);
    }
}
