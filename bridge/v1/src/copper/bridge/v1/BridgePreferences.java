package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.*;
import java.util.*;

/**
 * libGDX preferences in the desktop backend's format: one XML properties file per name under {@code .prefs/}.
 * The desktop game writes and reads this same layout, so a save directory moved between the two keeps its
 * settings.
 */
public class BridgePreferences implements Preferences {
    private final Properties properties = new Properties();
    private final FileHandle file;

    public BridgePreferences(FileHandle file) {
        this.file = file;
        if (!file.exists())
            return;
        try (InputStream in = new BufferedInputStream(file.read())) {
            properties.loadFromXML(in);
        } catch (Throwable e) {
            throw new GdxRuntimeException("failed to read " + file, e);
        }
    }

    @Override
    @BridgeOverride
    public Preferences putBoolean(String key, boolean val) {
        properties.put(key, Boolean.toString(val));
        return this;
    }

    @Override
    @BridgeOverride
    public Preferences putInteger(String key, int val) {
        properties.put(key, Integer.toString(val));
        return this;
    }

    @Override
    @BridgeOverride
    public Preferences putLong(String key, long val) {
        properties.put(key, Long.toString(val));
        return this;
    }

    @Override
    @BridgeOverride
    public Preferences putFloat(String key, float val) {
        properties.put(key, Float.toString(val));
        return this;
    }

    @Override
    @BridgeOverride
    public Preferences putString(String key, String val) {
        properties.put(key, val);
        return this;
    }

    @Override
    @BridgeOverride
    public Preferences put(Map<String, ?> vals) {
        for (Map.Entry<String, ?> entry : vals.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) putBoolean(entry.getKey(), (Boolean)value);
            else if (value instanceof Integer) putInteger(entry.getKey(), (Integer)value);
            else if (value instanceof Long) putLong(entry.getKey(), (Long)value);
            else if (value instanceof Float) putFloat(entry.getKey(), (Float)value);
            else if (value != null) putString(entry.getKey(), value.toString());
        }
        return this;
    }

    @Override
    @BridgeOverride
    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    @Override
    @BridgeOverride
    public int getInteger(String key) {
        return getInteger(key, 0);
    }

    @Override
    @BridgeOverride
    public long getLong(String key) {
        return getLong(key, 0L);
    }

    @Override
    @BridgeOverride
    public float getFloat(String key) {
        return getFloat(key, 0f);
    }

    @Override
    @BridgeOverride
    public String getString(String key) {
        return getString(key, "");
    }

    @Override
    @BridgeOverride
    public boolean getBoolean(String key, boolean defValue) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defValue)));
    }

    @Override
    @BridgeOverride
    public int getInteger(String key, int defValue) {
        return Integer.parseInt(properties.getProperty(key, Integer.toString(defValue)));
    }

    @Override
    @BridgeOverride
    public long getLong(String key, long defValue) {
        return Long.parseLong(properties.getProperty(key, Long.toString(defValue)));
    }

    @Override
    @BridgeOverride
    public float getFloat(String key, float defValue) {
        return Float.parseFloat(properties.getProperty(key, Float.toString(defValue)));
    }

    @Override
    @BridgeOverride
    public String getString(String key, String defValue) {
        return properties.getProperty(key, defValue);
    }

    @Override
    @BridgeOverride
    public Map<String, ?> get() {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String)entry.getKey();
            String value = (String)entry.getValue();
            if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
                map.put(key, Boolean.parseBoolean(value));
            else {
                try {
                    map.put(key, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    try {
                        map.put(key, Long.parseLong(value));
                    } catch (NumberFormatException e2) {
                        try {
                            map.put(key, Float.parseFloat(value));
                        } catch (NumberFormatException e3) {
                            map.put(key, value);
                        }
                    }
                }
            }
        }
        return map;
    }

    @Override
    @BridgeOverride
    public boolean contains(String key) {
        return properties.containsKey(key);
    }

    @Override
    @BridgeOverride
    public void clear() {
        properties.clear();
    }

    @Override
    @BridgeOverride
    public void remove(String key) {
        properties.remove(key);
    }

    @Override
    @BridgeOverride
    public void flush() {
        try (OutputStream out = new BufferedOutputStream(file.write(false))) {
            properties.storeToXML(out, null);
        } catch (Exception e) {
            throw new GdxRuntimeException("failed to write " + file, e);
        }
    }
}
