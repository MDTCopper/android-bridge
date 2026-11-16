package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import copper.bridge.gen.JvmCall;
import copper.bridge.util.Log;

import com.badlogic.gdx.*;

import com.badlogic.gdx.net.*;
import com.badlogic.gdx.utils.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * libGDX networking for the bridge. The game's multiplayer path uses KryoNet sockets, which need nothing from
 * here; only links and the HTTP requests for the server list cross this class.
 */
public class BridgeNet implements Net {

    @Override
    @BridgeOverride
    public void sendHttpRequest(HttpRequest request, HttpResponseListener listener) {
        Thread thread = new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)new URL(request.getUrl()).openConnection();
                connection.setRequestMethod(request.getMethod());
                connection.setConnectTimeout(request.getTimeOut());
                connection.setReadTimeout(request.getTimeOut());
                for (Map.Entry<String, String> header : request.getHeaders().entrySet())
                    connection.setRequestProperty(header.getKey(), header.getValue());

                String content = request.getContent();
                if (content != null && !content.isEmpty()) {
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(content.getBytes("UTF-8"));
                    }
                }

                int code = connection.getResponseCode();
                InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                byte[] body = read(in);
                Response response = new Response(code, body, connection.getHeaderFields());
                if (listener != null)
                    Gdx.app.postRunnable(() -> listener.handleHttpResponse(response));
            } catch (Throwable e) {
                if (listener != null)
                    Gdx.app.postRunnable(() -> listener.failed(e));
            }
        }, "bridge-http");
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] read(InputStream in) throws IOException {
        if (in == null)
            return new byte[0];
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1)
                out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    @Override
    @BridgeOverride
    public void cancelHttpRequest(HttpRequest httpRequest) {
    }

    @Override
    @BridgeOverride
    public ServerSocket newServerSocket(Protocol protocol, String hostname, int port, ServerSocketHints hints) {
        throw new GdxRuntimeException("server sockets are not part of the bridge");
    }

    @Override
    @BridgeOverride
    public ServerSocket newServerSocket(Protocol protocol, int port, ServerSocketHints hints) {
        throw new GdxRuntimeException("server sockets are not part of the bridge");
    }

    @Override
    @BridgeOverride
    public Socket newClientSocket(Protocol protocol, String host, int port, SocketHints hints) {
        throw new GdxRuntimeException("client sockets are not part of the bridge");
    }

    @Override
    @BridgeOverride
    public boolean openURI(String uri) {
        return JvmCall.openUri(uri);
    }

    /** One completed HTTP response, read fully before the listener sees it. */
    private static final class Response implements HttpResponse {
        private final int code;
        private final byte[] body;
        private final Map<String, List<String>> headers;

        Response(int code, byte[] body, Map<String, List<String>> headers) {
            this.code = code;
            this.body = body;
            this.headers = headers;
        }

        @Override
        @BridgeOverride
        public byte[] getResult() {
            return body;
        }

        @Override
        @BridgeOverride
        public String getResultAsString() {
            try {
                return new String(body, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return new String(body);
            }
        }

        @Override
        @BridgeOverride
        public InputStream getResultAsStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        @BridgeOverride
        public HttpStatus getStatus() {
            return new HttpStatus(code);
        }

        @Override
        @BridgeOverride
        public String getHeader(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        @Override
        @BridgeOverride
        public Map<String, List<String>> getHeaders() {
            return headers;
        }
    }
}
