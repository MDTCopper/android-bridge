package copper.bridge.util;

/**
 * Number parsing for the values this bridge reads out of text. All of these answer with a fallback instead of
 * throwing: the text comes from files another program wrote, and a malformed value must not turn into a stack
 * trace deep inside a class loader.
 */
public class Numbers {
    public static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isEmpty() ? fallback : Integer.parseInt(value.trim());
        } catch (Throwable e) {
            return fallback;
        }
    }

    public static double parseDouble(String value, double fallback) {
        try {
            return value == null || value.isEmpty() ? fallback : Double.parseDouble(value.trim());
        } catch (Throwable e) {
            return fallback;
        }
    }

    /**
     * Parses the digits out of a value, or the fallback when there are none; looser than {@link #parseInt} on
     * purpose - the fields of {@code version.properties} carry text around the number, and Mindustry's
     * {@code "custom build"} placeholder is not a number at all.
     */
    public static int digits(String value, int fallback) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (Throwable e) {
            return fallback;
        }
    }

    /**
     * Parses a release version such as {@code 159.7}, or answers zero; stricter than {@link #digits} on purpose,
     * since treating {@code "custom build"} as a version is how {@code 8.1597} gets derived from a 159.7 jar.
     */
    public static double version(String value) {
        if (value == null)
            return 0;
        String trimmed = value.trim();
        if (!trimmed.matches("[0-9]+(\\.[0-9]+)*"))
            return 0;
        try {
            return Double.parseDouble(trimmed);
        } catch (Throwable e) {
            return 0;
        }
    }
}
