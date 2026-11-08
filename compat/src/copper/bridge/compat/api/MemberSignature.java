package copper.bridge.compat.api;

import java.util.*;

/** One declaration as the check compares it: signature text without parameter names or annotations. */
public final class MemberSignature {
    public final String text;
    /** Parameter count for a method, -1 for a field. */
    public final int arity;
    /** Parameter type texts, in order; empty for a field. */
    public final List<String> params;

    public MemberSignature(String text, int arity, List<String> params) {
        this.text = text;
        this.arity = arity;
        this.params = params;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MemberSignature && ((MemberSignature) other).text.equals(text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
