package funcatron.intf;

import java.util.Map;
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
    Map<String, Object> getRequestInfo();

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

}
