package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.annotation.CompatRange;

import io.anuke.arc.*;
import io.anuke.arc.net.*;

import copper.bridge.gen.*;

/**
 * The floor's {@link Net}: it still has the HTTP and socket entry points that 83 removed. The bridge has no HTTP or
 * socket stack, so the calls fail explicitly instead of as an {@code AbstractMethodError}.
 */
@CompatRange(floor = "v65", ceiling = "v65")
final class NoNetLegacy implements Net {

    @BridgeOverride
    public void sendHttpRequest(HttpRequest request, HttpResponseListener listener) {
        if (listener != null)
            listener.failed(new IllegalStateException("no HTTP stack on the bridge"));
    }

    @BridgeOverride
    public void cancelHttpRequest(HttpRequest request) {
    }

    @BridgeOverride
    public ServerSocket newServerSocket(Protocol protocol, String host, int port, ServerSocketHints hints) {
        throw new IllegalStateException("no socket stack on the bridge");
    }

    @BridgeOverride
    public ServerSocket newServerSocket(Protocol protocol, int port, ServerSocketHints hints) {
        throw new IllegalStateException("no socket stack on the bridge");
    }

    @BridgeOverride
    public Socket newClientSocket(Protocol protocol, String host, int port, SocketHints hints) {
        throw new IllegalStateException("no socket stack on the bridge");
    }

    @BridgeOverride
    public boolean openURI(String uri) {
        return JvmCall.openUri(uri);
    }
}
