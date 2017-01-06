package funcatron.intf.impl;

import funcatron.intf.Accumulator;
import funcatron.intf.Context;
import funcatron.intf.ServiceVendor;
import funcatron.intf.ServiceVendorBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of Context so the Context Object associated with the classloader can be loaded
 */
public class ContextImpl implements Context, Accumulator {

    private final Map<Object, Object> data;
    private final Logger logger;
    private final CopyOnWriteArrayList<ReleasePair<?>> toTerminate = new CopyOnWriteArrayList<>();

    private static final ConcurrentHashMap<String, ServiceVendor<?>> services = new ConcurrentHashMap<>();

    public ContextImpl(Map<Object, Object> data, Logger logger) {
        this.data = data;
        this.logger = logger;
    }

    private static Map<String, Object> props = new HashMap<>();

    public static void initContext(Map<String, Object> props, ClassLoader loader,final  Logger logger) throws Exception {
        logger.log(Level.INFO,  () -> "Setting up context with props " + props);
        ContextImpl.props = props;
        ServiceLoader<ServiceVendorBuilder> builders =
        ServiceLoader.load(ServiceVendorBuilder.class, loader);

        HashMap<String, ServiceVendorBuilder> builderMap = new HashMap<>();

        builders.forEach(a -> builderMap.put(a.forType(), a));

        ServiceVendorBuilder db = new JDBCServiceVendorBuilder();

        builderMap.put(db.forType(), db);

        props.forEach((k, v) -> {if (null != k && k instanceof String && null != v && v instanceof Map) {
            Map m = (Map) v;
            Object o = m.get("type");
            if (null != o && o instanceof String) {
                logger.log(Level.FINER, () -> "Looking for builder for type: " + o);
                ServiceVendorBuilder b = builderMap.get(o);
                if (null != b) {
                    logger.log(Level.FINER, () -> "Building with props " + m);
                    Optional<ServiceVendor<?>> opt = b.buildVendor(k, m, logger);
                    opt.map(vendor -> services.put(k, vendor));

                }
          }
        }});
    }

    /**
     * Release all the resources held by the context
     */
    public static void endLife() {
        services.entrySet().forEach(a -> a.getValue().endLife());
    }

    private static class ReleasePair<T> {
        final T item;
        final ServiceVendor<T> vendor;

        ReleasePair(T item, ServiceVendor<T> vendor) {
            this.item = item;
            this.vendor = vendor;
        }
    }

    /**
     * Get a Map of all of the request information.
     *
     * @return key/value information for the request
     */
    @Override
    public Map<Object, Object> getRequestInfo() {
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
     * Get the http request headers
     *
     * @return the request headers
     */
    @Override
    public Map<String, Object> getHeaders() {
        return (Map<String, Object>) data.get("headers");
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

    @Override
    public <T> void accumulate(T item, ServiceVendor<T> vendor) {
        getLogger().log(Level.FINER, () -> "Accumulating "+item);
        toTerminate.add(new ReleasePair(item, vendor));
    }

    public void finished(boolean success) {
        getLogger().log(Level.FINER, () ->"Finished "+success+" Notifying "+toTerminate.size());
        toTerminate.forEach(a -> {
            // cast to something to avoid type error
            ReleasePair<Object> b = (ReleasePair<Object>) a;
            try {
                getLogger().log(Level.FINER, () -> "Releasing "+b.item);
                b.vendor.release(b.item, success);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception releasing " + a.item, e);
            }
            }
        );
    }

    /**
     * The name of the services available via this context
     *
     * @return the name of the services available
     */
    @Override
    public Set<String> services() {
        return services.keySet();
    }

    /**
     * Get the service for the given name
     *
     * @param name the name of the service
     * @return If the service exists, return
     */
    @Override
    public Optional<ServiceVendor<?>> serviceForName(String name) {
        if (!services.containsKey(name)) return Optional.empty();
        return Optional.of(services.get(name));
    }

    /**
     * Vend the named item for the type. E.g., `vendForName("database", java.sql.Connection.class)`
     *
     * @param name the name of the item to be vended
     * @param clz  the class of the item the class of the vended item
     * @return the Optional item
     * @throws Exception if there's a problem vending the item
     */
    @Override
    public <T> Optional<T> vendForName(String name, Class<T> clz) throws Exception {
        Optional<ServiceVendor<T>> ov = serviceForName(name, clz);
        if (ov.isPresent()) return Optional.of(ov.get().vend(this));
        return Optional.empty();
    }

    /**
     * Get the properties for this context
     *
     * @return properties for this context
     */
    @Override
    public Map<String, Object> properties() {
        return props;
    }
}
