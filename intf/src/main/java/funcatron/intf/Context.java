package funcatron.intf;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

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
     * Get the request params
     *
     * @return the request parameters
     */
    Map<String, List<String>> getRequestParams();

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
