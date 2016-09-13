package funcatron.intf;

/**
 * Implement this Interface for a Funcatron Endpoint.
 */
public interface Func<Request, Response> {
    /**
     * For each incoming request, a new `Func` instance is created and the `apply`
     * method is invoked.
     *
     * If the `Request` type is `Object` or `Map`, then the JSON body
     * of the incoming request is passed directly to the method.
     *
     * If the `Request` type is `String` or `byte[]`, then the raw request is passed in.
     *
     * If the `Request` type is `Node` or `Document`, then the request body is parsed using a DOM XML parser
     *
     * If the request method (found in the Context) is GET or DELETE, the `req` is null
     *
     * Regardless of the declared return type, here's the translation:
     * byte[] or String -- passed back
     * Node or Document -- considered XML, serialized to bytes accordingly
     * MetaResponse -- the contents of this object will determine the response
     *
     * @param req the incoming request. `null` if the request is a GET or DELETE
     *
     * @param context
     * @return
     */
    Response apply(Request req, Context context);
}
