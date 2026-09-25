package copper.bridge.gen.bus;

import copper.bridge.gen.Side;
import copper.bridge.gen.TypeRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One row of the bus: everything the generators need to know about it. */
final class Row {
    /** The bus name: the method name for a handler, the request name plus the outcome for an answer. */
    String name;
    /** The {@code Kinds} constant, after collisions were resolved. */
    String constant;
    Channel channel;
    /** The side whose handler this row calls: the side the pump performs it on. */
    Side ownerSide;
    /**
     * The side that calls this row, so it is the side whose call class holds the entry point. For a
     * handler row that is the other side; for an answer it is the side that answers, so a request and
     * its answer live in opposite call classes and only the request id ties them.
     */
    Side callSide;
    /** The class or interface the handler lives on, as Java names it. */
    String owner;
    /** The handler method, or the generated method an answer is delivered to. */
    String method;
    String descriptor;
    /** The handler's parameters, the leading request id included where there is one. */
    final List<TypeRef> params = new ArrayList<>();
    final List<String> paramNames = new ArrayList<>();
    /** The handler's return type. */
    TypeRef result;
    /** Whether the handler is a static method. */
    boolean isStatic;
    /** Whether the first parameter is the request id rather than payload. */
    boolean leadingRequest;
    /** Whether the caller blocks for the answer. */
    boolean waits;
    /** The outcomes of an asynchronous request. */
    final List<Callback> callbacks = new ArrayList<>();
    /** Whether the declaration accepted an object answer without a warning. */
    boolean acceptObjectAnswer;

    /** Assigned by sorting the constant names. */
    int id;
    String enumName;

    /** The payload: every parameter that is not the request id. */
    List<TypeRef> payload() {
        return leadingRequest ? params.subList(1, params.size()) : params;
    }

    /**
     * The names the generated native entry and its stub use: one per position, because one stub serves
     * every row of its shape and an answer row is declared from the model rather than from a method. The
     * public wrapper keeps the declaration's own names, which is the API a caller reads.
     */
    List<String> argNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < params.size(); i++)
            names.add("arg" + i);
        return names;
    }

    /** The type codes of every parameter, the request id included. */
    String paramCodes() {
        StringBuilder codes = new StringBuilder();
        for (TypeRef param : params)
            codes.append(param.code);
        return codes.toString();
    }

    /** The type codes of the payload only. */
    String payloadCodes() {
        StringBuilder codes = new StringBuilder();
        for (TypeRef param : payload())
            codes.append(param.code);
        return codes.toString();
    }

    /**
     * The parameters of the public wrapper, which keeps the names the declaration wrote: a request does
     * not expose the id the wrapper allocates, an answer does, because it has to pass that id back.
     */
    String javaParams() {
        return parameterList(leadingRequest && channel != Channel.ANSWER ? 1 : 0, false);
    }

    /**
     * The parameters of the native behind it, which always take the id the wrapper allocated. Named by
     * position, because the stub is shared by every row of its shape and no declaration owns its names.
     */
    String nativeParams() {
        return parameterList(0, true);
    }

    /** The JNI parameter list a stub takes after the kind id, named by position like the Java one. */
    String jniParameters() {
        List<String> names = argNames();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < params.size(); i++)
            text.append(", ").append(params.get(i).jni).append(' ').append(names.get(i));
        return text.toString();
    }

    /** The whole parameter list of a stub: the receiver, the kind id, and every parameter. */
    String jniSignature() {
        return ", jclass, jint kind" + (params.isEmpty() ? "" : jniParameters());
    }

    /** The C++ expression that reads this row's answer out of the jvalue the call wrote it to. */
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

    /**
     * The name of the generated native that serves it, in whichever call class holds the row: the Java
     * {@code native} method and the C++ stub are one entry, so neither needs a prefix of its own.
     */
    String entry() {
        return "call_" + shape();
    }

    /** The signature itself, which names the entry: the parameter codes and the return code. */
    private String shape() {
        String codes = paramCodes();
        return (codes.isEmpty() ? "" : codes + "_") + result.code;
    }
}
