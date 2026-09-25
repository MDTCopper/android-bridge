package copper.bridge.gen;

import java.util.Locale;

/** Spelling rules shared by the generated Java and C++. */
public final class Names {
    private Names() {
    }

    /** camelCase to SCREAMING_SNAKE: a word boundary is an upper-case letter after a lower-case
     * letter or a digit. */
    public static String constant(String name) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (i > 0 && Character.isUpperCase(current)
                    && (Character.isLowerCase(name.charAt(i - 1)) || Character.isDigit(name.charAt(i - 1))))
                text.append('_');
            text.append(Character.toUpperCase(current));
        }
        return text.toString();
    }

    /** SHOW_FILE_CHOOSER to ShowFileChooser; the underscore is the only word boundary. */
    public static String pascal(String constant) {
        StringBuilder text = new StringBuilder();
        for (String part : constant.split("_")) {
            if (part.isEmpty())
                continue;
            text.append(Character.toUpperCase(part.charAt(0)));
            text.append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return text.toString();
    }

    public static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** The name a type is written by where it is used: everything after the last dot. */
    public static String simpleName(String type) {
        final int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    /** The class holding one asynchronous call's callbacks: the request's name plus a suffix. */
    public static String holder(String method) {
        return capitalize(method) + "Holder";
    }
}
