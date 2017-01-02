package funcatron.intf;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The context of a Function Application
 */
public interface Context {
    /**
     * Get a Map of all of the request information.
     *
     * @return key/value information for the request
     */
    Map<Object, Object> getRequestInfo();

    /**
     * Get the logger
     *
     * @return the logger
     */
    Logger getLogger();

    /**
     * Get the URI of the request
     *
     * @return the URI of the request
     */
    String getURI();

    /**
     * Get the Swagger-processed parameters. The map contains sub-maps for "query" and "path"
     *
     * @return the request parameters
     */
    Map<String, Map<String, Object>> getRequestParams();

    /**
     * Get the Swagger-coerced query parameters
     * @return the swagger-coerced query params
     */
    Map<String, Object> getQueryParams();

    /**
     * Get the Swagger-coerced path parameters
     * @return the Swagger-coerced path parameters
     */
    Map<String, Object> getPathParams();

    /**
     * Get the Swagger-coerced body parameters
     * @return the Swagger-coerced body parameters
     */
    Map<String, Object> getBodyParams();

    /**
     * The merged path and query params
     * @return the merged path and query params
     */
    Map<String, Object> getMergedParams();

    /**
     * The request scheme (e.g., http, https)
     *
     * @return the request scheme
     */
    String getScheme();

    /**
     * The host the request was made on
     *
     * @return the name of the host
     */
    String getHost();

    /**
     * Return the request method (e.g., GET, POST, PUT, DELETE)
     *
     * @return the method
     */
    String getMethod();


    /**
     * The name of the services available via this context
     * @return the name of the services available
     */
    Set<String> services();

    /**
     * Get the service for the given name
     * @param name the name of the service
     * @return If the service exists, return
     */
    Optional<ServiceVendor<?>> serviceForName(String name);

    default <T> Optional<ServiceVendor<T>> serviceForName(String name, Class<T> clz) {
        return this.serviceForName(name).filter(a -> a.ofType(clz)).map(a -> (ServiceVendor<T>) a);
    }

    /**
     * Vend the named item for the type. E.g., `vendForName("database", java.sql.Connection.class)`
     * @param name the name of the item to be vended
     * @param clz the class of the item the class of the vended item
     * @param <T> the type that denotes the class
     * @return the Optional item
     * @throws Exception if there's a problem vending the item
     */
    <T> Optional<T> vendForName(String name, Class<T> clz) throws Exception;

    /**
     * Get the properties for this context
     * @return properties for this context
     */
    Map<String, Object> properties();

}
