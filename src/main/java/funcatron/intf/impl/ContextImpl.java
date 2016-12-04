package funcatron.intf.impl;

import funcatron.intf.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * An implementation of Context so the Context Object associated with the classloader can be loaded
 */
public class ContextImpl implements Context {

    private final Map<String, Object> data;
    private final Logger logger;

    public ContextImpl(Map<String, Object> data, Logger logger) {
        this.data = data;
        this.logger = logger;
    }

    /**
     * Get a Map of all of the request information.
     *
     * @return key/value information for the request
     */
    @Override
    public Map<String, Object> getRequestInfo() {
        return data;
    }

    /**
     * Get the logger
     *
     * @return the logger
     */
    @Override
    public Logger getLogger() {
        return logger;
    }

    /**
     * Get the URI of the request
     *
     * @return the URI of the request
     */
    @Override
    public String getURI() {
        return (String) data.get("uri");
    }

    /**
     * Get the Swagger-processed parameters. The map contains sub-maps for "query" and "path"
     *
     * @return the request parameters
     */
    @Override
    public Map<String, Map<String, Object>> getRequestParams() {
        return (Map<String, Map<String, Object>>) data.get("parameters");
    }


    /**
     * Get the Swagger-coerced path parameters
     *
     * @return the Swagger-coerced path parameters
     */
    @Override
    public Map<String, Object> getPathParams() {
        Map<String, Map<String, Object>> parameters = (Map<String, Map<String, Object>>) data.get("parameters");
        if (null == parameters) parameters = new HashMap<>();
        Map<String, Object> ret = parameters.get("path");
        if (null == ret) ret = new HashMap<>();
        return ret;
    }

    /**
     * Get the Swagger-coerced body parameters
     *
     * @return the Swagger-coerced body parameters
     */
    @Override
    public Map<String, Object> getBodyParams() {
        Map<String, Map<String, Object>> parameters = (Map<String, Map<String, Object>>) data.get("parameters");
        if (null == parameters) parameters = new HashMap<>();
        Map<String, Object> ret = parameters.get("body");
        if (null == ret) ret = new HashMap<>();
        return ret;
    }

    /**
     * The merged path and query params
     *
     * @return the merged path and query params
     */
    @Override
    public Map<String, Object> getMergedParams() {
        Map<String, Object> path = getPathParams();
        Map<String, Object> query = getQueryParams();
        HashMap<String, Object> ret = new HashMap<>();

        path.forEach((k,v) -> ret.put(k,v));
        query.forEach((k,v) -> ret.put(k,v));

        return ret;
    }

    /**
     * Get the Swagger-coerced query parameters
     * @return the swagger-coerced query params
     */
    @Override
    public Map<String, Object> getQueryParams() {
        Map<String, Map<String, Object>> parameters = (Map<String, Map<String, Object>>) data.get("parameters");
        if (null == parameters) parameters = new HashMap<>();
        Map<String, Object> ret = parameters.get("query");
        if (null == ret) ret = new HashMap<>();
        return ret;
    }


    /**
     * The request scheme (e.g., http, https)
     *
     * @return the request scheme
     */
    @Override
    public String getScheme() {
        return (String) data.get("scheme");
    }

    /**
     * The host the request was made on
     *
     * @return the name of the host
     */
    @Override
    public String getHost() {
        return (String) data.get("host");
    }

    /**
     * Return the request method (e.g., GET, POST, PUT, DELETE)
     *
     * @return the method
     */
    @Override
    public String getMethod() {
        return (String) data.get("request-method");
    }

    /**
     * Return version information so the Runner can do the right thing
     * @return version information so the Runner can do the right thing
     */
    public static String getVersion() {
        return "1";
    }
}
