package funcatron.intf;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Constants related to Funcatron. For all Functions associated with finding or setting named functions
 * of certain types, here are the named functions along with the expected function signature. Note that
 * function signatures with Void as a parameter mean that the parameter is ignored and {@code null} should
 * be passed.
 */
public interface Constants {
    /**
     * Get a list of all the known Operations for the Context
     */
    public static String OperationsConst = "operations";

    /**
     * The type signature for operations
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Void, Logger, Set<String>>>
            ConvertOperationsFunc = a -> (BiFunction<Void, Logger, Set<String>>) ((Object) a);


    /**
     * The constant name for the function that's applied at the end of life for a context
     */
    public static String EndLifeConst = "endLife";

    /**
     * The type signature for the end of life function
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Void, Logger, Void>>
            ConvertEndLifeFunc = a -> (BiFunction<Void, Logger, Void>) ((Object) a);

    /**
     * The constant name for the function that returns the ClassLoader for the current Context
     */
    public static String GetClassloaderConst = "getClassloader";

    /**
     * The type signature for the getClassLoader function
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Void, Logger, ClassLoader>>
            ConvertGetClassloaderFunc = a -> (BiFunction<Void, Logger, ClassLoader>) ((Object) a);

    /**
     * Return the Swagger for the current Func Bundle
     */
    public static String GetSwaggerConst = "getSwagger";

    /**
     * The type signature for the getSwagger function. The return Map should have two entries: "type" which
     * is one of "json", "yaml", or "map" and the entry for "swagger". If the "type" is "json", the "swagger"
     * entry should be a String which is parsed as JSON. If the "type" is "yaml", the "swagger" entry should
     * be a String which is parsed using a YAML parser. If the "type" is "map", the "swagger" field should be
     * a {@code Map<String, Object>} that contains the parsed Swagger.
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Void, Logger, Map<String, Object>>>
            ConvertGetSwaggerFunc = a -> (BiFunction<Void, Logger, Map<String, Object>>) ((Object) a);

    /**
     * Return the version number of the funcatron/intf package
     */
    public static String GetVersionConst = "getVersion";

    /**
     * Takes no parameters and returns a String
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Void, Logger, String>>
            ConvertGetVersionFunc = a -> (BiFunction<Void, Logger, String>) ((Object) a);

    /**
     * get a function that will computer a dispatcher function
     */
    public static String DispatcherForConst = "dispatcherFor";

    /**
     * Given the Swagger entry for an endpoint, return the dispatcher for that entry. The "$operationId" field contains
     * the operationId associated with the endpoint. The rest of the endpoint definition is the rest of the map.
     * The "$deserializer" field contains the built in deserializer function. The "$serializer" function.
     * <br>
     * The returned function performs the dispatch for the endpoint where the {@code InputStream} is
     * the request body, the Map is the request information. The function returns a response Map that must
     * have the "status", "headers", and "body" fields set.
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Map<Object, Object>, Logger, BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>>>
            ConvertDispatcherForFunc = a -> (BiFunction<Map<Object, Object>, Logger, BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>>) ((Object) a);

    /**
     * The function name that will return a global serializer for the func bundle
     */
    public static String GetSerializerConst = "getSerializer";

    /**
     * Return a function that will take a response object, a mime type, and return a {@code byte[]} representing the bytes to return. If null
     * is returned, then the built-in serializer will be used.
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Map<Object, Object>, Logger, BiFunction<Object, String, byte[]>>>
            ConvertGetSerializerFunc = a -> (BiFunction<Map<Object, Object>, Logger, BiFunction<Object, String, byte[]>>) ((Object) a);


    /**
     * The function name for the global deserializer
     */
    public static String GetDeserializerConst = "getDeserializer";

    /**
     * Return a function that performs deserialization of an {@code InputStream} into a reified Object. The return function takes the
     * the InputStream which is the request body and a {@code List<Object>} where the first entries are a String with the contentType and a {@code Class<?>} paramType
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Map<Object, Object>, Logger, BiFunction<InputStream, List<Object>, Object>>>
            ConvertGetDeserializerFunc = a -> (BiFunction<Map<Object, Object>, Logger, BiFunction<InputStream, List<Object>, Object>>) ((Object) a);


    /**
     * the name of the middleware wrapping function
     */
    public static String WrapWithMiddlewareConst = "wrapWithMiddleware";

    /**
     * Pass in a Map with the "function" parameter set the dispatcher function type {@code BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>} and wrap
     * in the middleware
     */
    public static Function<BiFunction<Map<Object, Object>, Logger, Object>, BiFunction<Map<Object, Object>, Logger, BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>>>
            ConvertWrapWithMiddlewareFunc = a -> (BiFunction<Map<Object, Object>, Logger, BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>>) ((Object) a);


}
