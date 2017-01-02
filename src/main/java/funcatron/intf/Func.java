package funcatron.intf;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implement this Interface for a Funcatron Endpoint.
 */
public interface Func<Request> {
    /**
     * For each incoming request, a new `Func` instance is created and the `apply`
     * method is invoked.
     *
     * If the `Request` type is `Object` then the JSON body
     * of the incoming request is passed directly to the method.
     *
     * If the `Request` type is `String` or `byte[]` or `InputStream`, then the raw request is passed in.
     *
     * If the `Request` type is `Node` or `Document`, then the request body is parsed using a DOM XML parser
     *
     * If the request method (found in the Context) is GET or DELETE, the `req` is null
     *
     * The translation or return type:
     * byte[], OutputStream -- passed back
     * Node or Document -- considered XML, serialized to bytes accordingly
     * Map or anything that's not listed -- JSON serialize
     * MetaResponse -- the contents of this object will determine the response
     *
     * @param req the incoming request. `null` if the request is a GET or DELETE
     *
     * @param context
     *
     * @throws Exception if there's a problem. A 500 will be returned to the caller
     *
     * @return
     */
    Object apply(Request req, Context context) throws Exception;

    /**
     * A function that will take the JSON data from the initial parse in Jackson
     * and convert it
     * into a Request object. This is a good place to set up a Jackson
     * parser with custom Jackson stuff rather than relying on the bare
     * bones Jackson parser in the Runner.
     *
     * @return a customer JSON to Object parser or null if Funcatron should use the default Jackson implementation
     */
    default Function<InputStream, Request> jsonDecoder() {
        return null;
    }


    /**
     * A customer object serializer that will take a return value and JSON serialize it as a byte array. This is
     * a good place use a custom Jackson serializer. If null is returned, then use the default Funcatron Jackson
     * implementation
     *
     * @return The object to JSON serializer or null if there's no customer serializer
     */
    default Function<Object, byte[]> jsonEncoder() {
        return null;
    }

    /**
     * If the content type is not the default type, return it here
     * @return the content type
     */
    default String contentType() {return null;}

    /**
     * The HTTP response status code
     * @return the status code
     */
    default int statusCode() {return 200;}

    /**
     * The HTTP response headers
     * @return response headers
     */
    default Map<String, Object> headers() {
        return new HashMap<>();
    }
}
