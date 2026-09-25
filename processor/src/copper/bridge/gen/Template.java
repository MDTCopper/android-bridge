package copper.bridge.gen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One piece of generated text with named holes in it.
 *
 * <p>{@code {{name}}} marks a hole and {@link #with} fills it at the column the hole sat at, so a
 * nested piece keeps its own indentation and the file's shape stays in one piece instead of being
 * spread over appending statements.</p>
 * <p>A value is a run of lines whose leading and trailing newlines are dropped, so the template
 * decides the layout; an empty value alone on its line takes that line away.</p>
 */
public final class Template {
    /** What a hole looks like: a name between these, and nothing else in between. */
    private static final String OPEN = "{{";
    private static final String CLOSE = "}}";

    private final String pattern;
    private final Map<String, String> values = new LinkedHashMap<>();

    private Template(String pattern) {
        this.pattern = pattern;
    }

    public static Template of(String pattern) {
        return new Template(pattern);
    }

    /** Fills one hole. A null value is the empty one, which is how an optional piece is left out. */
    public Template with(String name, Object value) {
        values.put(name, value == null ? "" : value.toString());
        return this;
    }

    /** Renders each template in order with a separator, so the layout between items belongs to the list. */
    public static String join(Iterable<Template> parts, String separator) {
        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (Template part : parts) {
            if (!first)
                text.append(separator);
            text.append(trim(part.render()));
            first = false;
        }
        return text.toString();
    }

    public String render() {
        StringBuilder text = new StringBuilder(pattern.length() + 64);
        int at = 0;
        while (true) {
            int open = pattern.indexOf(OPEN, at);
            if (open < 0) {
                text.append(pattern, at, pattern.length());
                return text.toString();
            }
            int close = pattern.indexOf(CLOSE, open + OPEN.length());
            if (close < 0)
                throw new IllegalStateException("a hole is never closed: " + excerpt(open));
            String name = pattern.substring(open + OPEN.length(), close);
            String value = values.get(name);
            if (value == null)
                throw new IllegalStateException("no value for the hole " + name);
            value = trim(value);

            int lineStart = pattern.lastIndexOf('\n', open - 1) + 1;
            int lineEnd = pattern.indexOf('\n', close + CLOSE.length());
            String before = pattern.substring(lineStart, open);
            String after = lineEnd < 0 ? pattern.substring(close + CLOSE.length())
                    : pattern.substring(close + CLOSE.length(), lineEnd);
            if (before.isBlank() && after.isBlank()) {
                // The template's own newline is what ends a whole-line value.
                text.append(pattern, at, lineStart);
                if (!value.isEmpty()) {
                    text.append(move(value, indentOf(lineStart, open), true));
                    text.append('\n');
                }
                at = lineEnd < 0 ? pattern.length() : lineEnd + 1;
            } else {
                text.append(pattern, at, open);
                text.append(move(value, indentOf(lineStart, open), false));
                at = close + CLOSE.length();
            }
        }
    }

    /**
     * Takes the newlines off both ends, and a trailing all-white line with them, so an item never
     * carries a blank line to separate itself from the next one.
     */
    private static String trim(String value) {
        int start = 0;
        while (start < value.length() && value.charAt(start) == '\n')
            start++;
        int end = value.length();
        while (end > start && value.charAt(end - 1) == '\n')
            end--;
        int lastLine = value.lastIndexOf('\n', end - 1) + 1;
        if (lastLine > start && value.substring(lastLine, end).isBlank())
            end = lastLine - 1;
        return value.substring(start, end);
    }

    /** The white space the hole's line starts with, which is where a multi-line value is moved to. */
    private String indentOf(int lineStart, int open) {
        int end = lineStart;
        while (end < open && (pattern.charAt(end) == ' ' || pattern.charAt(end) == '\t'))
            end++;
        return pattern.substring(lineStart, end);
    }

    /**
     * Puts the value at the hole line's indentation. A whole-line hole replaces that indentation,
     * so every line moves; an inline hole keeps the text before it, so only the later lines move. A
     * blank line stays blank - indenting it would put white space into the generated file.
     */
    private static String move(String value, String indent, boolean wholeLine) {
        if (indent.isEmpty() || value.isEmpty())
            return value;
        StringBuilder text = new StringBuilder(value.length() + 16);
        int at = 0;
        if (!wholeLine) {
            int first = value.indexOf('\n');
            if (first < 0)
                return value;
            text.append(value, 0, first + 1);
            at = first + 1;
        }
        while (at < value.length()) {
            int end = value.indexOf('\n', at);
            if (end < 0) {
                text.append(indent).append(value, at, value.length());
                break;
            }
            if (end == at)
                text.append('\n');
            else
                text.append(indent).append(value, at, end + 1);
            at = end + 1;
        }
        return text.toString();
    }

    private String excerpt(int at) {
        return pattern.substring(at, Math.min(pattern.length(), at + 40));
    }
}
