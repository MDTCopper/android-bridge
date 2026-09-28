package copper.bridge.gen.bus;

import copper.bridge.gen.Side;
import copper.bridge.gen.TypeRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One row of the bus: everything the generators need to know about it. */
final class Row {
    String name;
    String constant;
    Channel channel;
    Side ownerSide;
    /** The side that calls this row: for a handler the other side, for an answer the side that answers, so a request
     *  and its answer sit in opposite call classes with only the request id tying them. */
    Side callSide;
    String owner;
    String method;
    String descriptor;
    final List<TypeRef> params = new ArrayList<>();
    final List<String> paramNames = new ArrayList<>();
    TypeRef result;
    boolean isStatic;
    boolean leadingRequest;
    boolean waits;
    final List<Callback> callbacks = new ArrayList<>();
    boolean acceptObjectAnswer;

    int id;
    String enumName;

    List<TypeRef> payload() {
        return leadingRequest ? params.subList(1, params.size()) : params;
    }

    /** The names the generated native entry and its stub use, one per position, since one stub serves every row of
     *  its shape. The public wrapper keeps the declaration's own names. */
    List<String> argNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < params.size(); i++)
            names.add("arg" + i);
        return names;
    }

    String paramCodes() {
        StringBuilder codes = new StringBuilder();
        for (TypeRef param : params)
            codes.append(param.code);
        return codes.toString();
    }

    String payloadCodes() {
        StringBuilder codes = new StringBuilder();
        for (TypeRef param : payload())
            codes.append(param.code);
        return codes.toString();
    }

    /** The parameters of the public wrapper, which keeps the declaration's names: a request does not expose the id
     *  the wrapper allocates, an answer does, because it has to pass that id back. */
    String javaParams() {
        return parameterList(leadingRequest && channel != Channel.ANSWER ? 1 : 0, false);
    }

    String nativeParams() {
        return parameterList(0, true);
    }

    String jniParameters() {
        List<String> names = argNames();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < params.size(); i++)
            text.append(", ").append(params.get(i).jni).append(' ').append(names.get(i));
        return text.toString();
    }

    String jniSignature() {
        return ", jclass, jint kind" + (params.isEmpty() ? "" : jniParameters());
    }

    String jniAnswer() {
        String value = "answer." + result.jvalue;
        return result.object ? "(" + result.jni + ") " + value : value;
    }

    /** The rows keyed by their native entry name, so one signature is emitted once. */
    static Map<String, Row> shapes(List<Row> rows) {
        Map<String, Row> shapes = new LinkedHashMap<>();
        for (Row row : rows)
            shapes.putIfAbsent(row.entry(), row);
        return shapes;
    }

    private String parameterList(int first, boolean positional) {
        List<String> names = positional ? argNames() : paramNames;
        StringBuilder text = new StringBuilder();
        for (int i = first; i < params.size(); i++) {
            if (text.length() > 0)
                text.append(", ");
            text.append(params.get(i).java).append(' ').append(names.get(i));
        }
        return text.toString();
    }

    /** The generated native that serves it: the Java {@code native} method and the C++ stub are one entry. */
    String entry() {
        return "call_" + shape();
    }

    private String shape() {
        String codes = paramCodes();
        return (codes.isEmpty() ? "" : codes + "_") + result.code;
    }
}
