package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;
import copper.bridge.util.Log;

import com.badlogic.gdx.ApplicationLogger;

import java.io.*;

/**
 * libGDX's application logger, in the desktop backend's format: {@code tag: message}, with a throwable's stack
 * trace following it. The lines go through the bridge log, which is what turns them into the one log file.
 */
public class BridgeLogger implements ApplicationLogger {

    @Override
    @BridgeOverride
    public void log(String tag, String message) {
        Log.info(tag + ": " + message);
    }

    @Override
    @BridgeOverride
    public void log(String tag, String message, Throwable exception) {
        Log.info(tag + ": " + message);
        Log.info(stackTrace(exception));
    }

    @Override
    @BridgeOverride
    public void error(String tag, String message) {
        Log.error(tag + ": " + message);
    }

    @Override
    @BridgeOverride
    public void error(String tag, String message, Throwable exception) {
        Log.error(tag + ": " + message);
        Log.error(stackTrace(exception));
    }

    @Override
    @BridgeOverride
    public void debug(String tag, String message) {
        Log.debug(tag + ": " + message);
    }

    @Override
    @BridgeOverride
    public void debug(String tag, String message, Throwable exception) {
        Log.debug(tag + ": " + message);
        Log.debug(stackTrace(exception));
    }

    private static String stackTrace(Throwable exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
