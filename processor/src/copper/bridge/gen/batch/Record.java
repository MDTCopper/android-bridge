package copper.bridge.gen.batch;

import copper.bridge.gen.Names;
import java.util.ArrayList;
import java.util.List;

/** One record kind of a schema: an abstract method on the receiving side. */
final class Record {
    /** The method name, which is what the sender calls. */
    String name;
    /** The id this record travels as, assigned by declaration order. */
    int id;
    final List<Field> params = new ArrayList<>();
    /** The source forms each parameter accepts, one list per {@code @SenderSignature}. */
    final List<List<String>> signatures = new ArrayList<>();

    /** The wire parameter list, which is the receiving method's own signature. */
    String wireParameters() {
        StringBuilder text = new StringBuilder();
        for (Field param : params) {
            if (text.length() > 0)
                text.append(", ");
            text.append(param.java()).append(' ').append(param.name);
        }
        return text.toString();
    }

    /** Whether every parameter is a scalar; such a record needs no overloads. */
    boolean scalarsOnly() {
        for (Field param : params) {
            if (param.isSequence())
                return false;
        }
        return true;
    }

    /**
     * The buffer one record's sequence lives in: both sides name it record plus field, which cannot
     * collide with a header field or a record name.
     */
    String fieldName(Field param) {
        return name + "_" + param.name;
    }

    /** The arguments of the call into this record's wire form: a sequence comes from its own buffer. */
    String wireArguments() {
        StringBuilder text = new StringBuilder();
        for (Field param : params) {
            if (text.length() > 0)
                text.append(", ");
            text.append(param.isSequence() ? fieldName(param) : param.name);
        }
        return text.toString();
    }

    /**
     * The parameters of one listed source form: a scalar is the wire type, a sequence takes the form
     * listed at its own index.
     */
    String sourceParameters(List<String> signature) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (text.length() > 0)
                text.append(", ");
            Field param = params.get(i);
            final String type = param.isSequence() ? Names.simpleName(signature.get(i)) : param.scalar.java;
            text.append(type).append(' ').append(param.name);
        }
        return text.toString();
    }

    /** Whether one listed source form is exactly the wire form, in which case it needs no overload. */
    boolean sameAsWire(List<String> signature) {
        if (signature.size() != params.size())
            return false;
        for (int i = 0; i < signature.size(); i++) {
            Field param = params.get(i);
            final String form = Names.simpleName(signature.get(i));
            if (param.isSequence() ? !param.sequence.simple.equals(form) : !param.scalar.java.equals(form))
                return false;
        }
        return true;
    }

    /** The first listed source form that is not the wire form itself, or null when there is none. */
    List<String> firstSourceForm() {
        for (List<String> signature : signatures) {
            if (!sameAsWire(signature))
                return signature;
        }
        return null;
    }
}
