package funcatron.intf.impl;

import funcatron.intf.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


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

    private volatile static ClassLoader contextClassloader;

    public static ClassLoader getClassloader() {
        ClassLoader ret = contextClassloader;
        if (null == ret) {
            return ContextImpl.class.getClassLoader();
        }
        return ret;
    }

    private static List<MiddlewareProvider> middleWare = new ArrayList<>();

    /**
     * A list of the operations we speak
     */
    private static final ConcurrentHashMap<String, BiFunction<Map<Object, Object>, Logger, Object>> operations = new ConcurrentHashMap<>();

    static {
        operations.put("operations", (x, l) -> operations.keySet());
        operations.put("endLife", (x, l) -> {
            endLife(l);
            return null;
        });
        operations.put("getClassloader", (x, l) -> getClassloader());
        operations.put("getSwagger", (x, l) -> getSwagger(l));
        operations.put("getVersion", (x, l) -> getVersion(l));
        operations.put("dispatcherFor", (x, l) -> dispatcherFor(x, l));
        operations.put("getSerializer", (x, l) -> null);
        operations.put("getDeserializer", (x, l) -> null);
        operations.put("wrapWithMiddleware", (x, l) -> wrapWithMiddleware((BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>) x.get("function")));
    }

    /**
     * Slurp an input stream to a byte array... why this isn't part of the JDK is stupid
     *
     * @param is the inputstream
     * @return the resulting byte array
     */
    public static byte[] toByteArray(InputStream is) {
        if (null == is) return new byte[0];

        byte[] buf = new byte[4096];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int cnt;

        try {
            for (cnt = is.read(buf); cnt >= 0; cnt = is.read(buf)) {
                if (cnt > 0) {
                    bos.write(buf, 0, cnt);
                }
            }
        } catch (IOException io) {
            throw new RuntimeException("Failed to read", io);
        }

        return bos.toByteArray();
    }


    private static Map<String, Object> reifyToMap(InputStream is, String type) {
        try {
            String s = new String(toByteArray(is), "UTF-8");
            Map<String, Object> ret = new HashMap<>();
            ret.put("swagger", s);
            ret.put("type", type);
            return ret;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e ) {
            throw new RuntimeException("Failed to reify Swagger", e);
        }
    }

    public static Map<String, Object> getSwagger(Logger l) {
        InputStream is = contextClassloader.getResourceAsStream("funcatron.yaml");
        if (null != is) {
            return reifyToMap(is, "yaml");
        }

        is = contextClassloader.getResourceAsStream("funcatron.json");
        if (null != is) {
            return reifyToMap(is, "json");
        }

        return null;
    }

    public static Object dispatcherFor(Map<Object, Object> info, Logger l) {
        Dispatcher disp = new Dispatcher();
        return wrapWithMiddleware(disp.apply((String) info.get("$operationId"), info));
    }

    public static BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> wrapWithMiddleware(BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> function) {
        for (MiddlewareProvider mw : middleWare) {
            function = mw.wrap(function);
        }
        return function;
    }

    /**
     * In order to minimize the classes needed to bridge between a loaded Func Bundle and the Runner, there
     * are a series of named "operations" that are functions that the Runner can query from the Func Bundle
     * and invoke. It also may be that stuff within the Func Bundle does the same. Thus, we provide a list
     * of named operations.
     *
     * @return the known, named operations
     */
    public static Set<String> operationNames() {
        return operations.keySet();
    }

    public static void addOperation(String name, BiFunction<Map<Object, Object>, Logger, Object> func) {
        operations.put(name, func);
    }

    private static final CopyOnWriteArrayList<Function<Logger, Void>> endOfLifeFunctions = new CopyOnWriteArrayList<>();

    public static void addFunctionToEndOfLife(Function<Logger, Void> func) {
        endOfLifeFunctions.add(func);
    }

    private static <T> List<T> toList(Iterable<T> it) {
        return StreamSupport.stream(it.spliterator(), false).collect(Collectors.toList());
    }

    private static <T extends OrderedProvider> List<T> toSortedList(Iterable<T> it) {
        List<T> list = toList(it);
        list.sort((a, b) -> b.order() - a.order());
        return list;
    }

    public static Function<String, BiFunction<Map<Object, Object>, Logger, Object>>
    initContext(Map<String, Object> props, ClassLoader loader, final Logger logger) throws Exception {
        logger.log(Level.INFO, () -> "Setting up context with props " + props);

        contextClassloader = loader;


        ContextImpl.props = Collections.unmodifiableMap(new HashMap<>(props));

        // get the ClassloaderProvider services
        List<ClassloaderProvider> classloaderMagic = toSortedList(ServiceLoader.load(ClassloaderProvider.class, loader));


        ClassLoader curClassloader = loader;
        for (ClassloaderProvider clb : classloaderMagic) {
            curClassloader = clb.buildFrom(curClassloader, eol -> {
                addFunctionToEndOfLife(eol);
                return null;
            }, logger);
        }
        contextClassloader = curClassloader;

        toSortedList(ServiceLoader.load(OperationProvider.class, curClassloader)).forEach(i -> {
            try {
                i.installOperation((name, func) -> {
                            addOperation(name, func);
                            return null;
                        }, s -> operations.get(s),
                        eol -> {
                            addFunctionToEndOfLife(eol);
                            return null;
                        }, contextClassloader, logger);
            } catch (Exception e) {
                logger.log(Level.WARNING, e, () -> "Failed to install operation");
            }
        });

        ServiceLoader<ServiceVendorProvider> builders =
                ServiceLoader.load(ServiceVendorProvider.class, curClassloader);

        HashMap<String, ServiceVendorProvider> builderMap = new HashMap<>();

        builders.forEach(a -> builderMap.put(a.forType(), a));

        ServiceVendorProvider db = new JDBCServiceVendorBuilder();

        builderMap.put(db.forType(), db);

        props.forEach((k, v) -> {
            if (null != k && k instanceof String && null != v && v instanceof Map) {
                Map m = (Map) v;
                Object o = m.get("type");
                if (null != o && o instanceof String) {
                    logger.log(Level.FINER, () -> "Looking for builder for type: " + o);
                    ServiceVendorProvider b = builderMap.get(o);
                    if (null != b) {
                        logger.log(Level.FINER, () -> "Building with props " + m);
                        Optional<ServiceVendor<?>> opt = b.buildVendor(k, m, logger);
                        opt.map(vendor -> services.put(k, vendor));

                    }
                }
            }
        });

        middleWare = toSortedList(ServiceLoader.load(MiddlewareProvider.class, curClassloader));

        return (name) -> operations.get(name);
    }

    /**
     * Release all the resources held by the context
     */
    public static void endLife() {
        endLife(Logger.getLogger(ContextImpl.class.getName()));
    }

    public static <T> T runOperation(String name, Map<Object, Object> params, Logger logger, Class<T> clz) {
        BiFunction<Map<Object, Object>, Logger, Object> theOp = operations.get(name);

        if (null == theOp) return null;

        Object ret = theOp.apply(params, logger);

        if (clz.isInstance(ret)) return (T) ret;
        return null;
    }

    /**
     * Release all the resources held by the context... with a logger
     */
    public static void endLife(Logger logger) {
        if (null == logger) logger = Logger.getLogger(ContextImpl.class.getName());
        final Logger fl = logger;
        services.entrySet().forEach(a -> {
            try {
                a.getValue().endLife();
            } catch (Exception e) {
                fl.log(Level.WARNING, e, () -> "Failed to end resource life");
            }
        });

        endOfLifeFunctions.forEach(a -> {
            try {
                a.apply(fl);
            } catch (Exception e) {
                fl.log(Level.WARNING, e, () -> "Failed to end resource life");
            }
        });
    }

    public static Callable<Object> buildParameterResolver(final InputStream inputStream,
                                                          final Func<Object> func,
                                                          final Function<InputStream, Object> basicDeserializer,
                                                          final BiFunction<InputStream, Class<?>, Object> deserializer,
                                                          final Class<?> paramType,
                                                          final String contentType,
                                                          final Logger logger) {
        Callable<Object> resolveParam = () -> {

            BiFunction<InputStream, List<Object>, Object> contextDeserializer =
                    runOperation("getDeserializer",
                    new HashMap<>(), logger, BiFunction.class);

            Object param = null;
            if (null != inputStream) {
                Function<InputStream, Object> instDeserializer = null;

                try {
                    if (null != func) instDeserializer = func.jsonDecoder();
                } catch (UnsupportedOperationException e) {
                    // ignore ... the implementation didn't get the Java default method... sigh
                }

                if (null != instDeserializer) {
                    param = instDeserializer.apply(inputStream);
                } else if (null != contextDeserializer) {
                    param = contextDeserializer.apply(inputStream, Arrays.asList(new Object[] {contentType, paramType}));
                } else if (null != basicDeserializer) {
                    param = basicDeserializer.apply(inputStream);
                } else {
                    param = deserializer.apply(inputStream, paramType);
                }
            }

            return param;
        };

        return resolveParam;
    }

    public static BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>
    makeResponseBiFunc(Function<Object, byte[]> serializer,
                                            BiFunction<InputStream, Class<?>, Object> deserializer,
                                            Function<InputStream, Object> basicDeserializer,
                                            String className,
                                            Class<?> paramType, Class<Func<Object>> c) {
        return (InputStream inputStream, Map<Object, Object> om) -> {
            Logger theLogger = (Logger) om.get("$logger");
            if (null == theLogger) theLogger = Logger.getLogger(className);
            final ContextImpl theContext = new ContextImpl(om, theLogger);

            try {

                final Func<Object> func = c.newInstance();

                Callable<Object> resolveParam = ContextImpl.buildParameterResolver(inputStream, func,
                        basicDeserializer, deserializer, paramType, theContext.contentType(), theLogger);

                Object retVal = null;

                if ("get".equals(theContext.getMethod())) retVal = func.get(theContext);
                else if ("post".equals(theContext.getMethod())) retVal = func.post(resolveParam.call(), theContext);
                else if ("put".equals(theContext.getMethod())) retVal = func.put(resolveParam.call(), theContext);
                else if ("patch".equals(theContext.getMethod())) retVal = func.patch(resolveParam.call(), theContext);
                else if ("delete".equals(theContext.getMethod())) retVal = func.delete(theContext);
                else retVal = func.apply(resolveParam.call(), theContext);


                return ContextImpl.responseObjectToResponseMap(func, retVal, theContext, serializer, new HashMap<>(), theLogger);

            } catch (RuntimeException re) {
                theContext.finished(false);
                throw re;
            } catch (Exception e) {
                theContext.finished(false);
                throw new RuntimeException("Failed to apply function", e);
            }
        };
    }

    public static Map<Object, Object> responseObjectToResponseMap(Func<Object> func,
                                                                  Object retVal,
                                                                  Context theContext,
                                                                  Function<Object, byte[]> serializer,
                                                                  Map<Object, Object> altResponseInfo,
                                                                  Logger logger) throws Exception {
        boolean allSet = false;
        Map<Object, Object> ret = new HashMap<>();
        Function<Object, byte[]> instSerializer = null;

        try {
            if (null != func) instSerializer = func.jsonEncoder();
        } catch (UnsupportedOperationException e) {
            // ignore ... the implementation didn't get the Java default method... sigh
        }

        String contentType =  "application/json" ;

        if (null == retVal) retVal = new byte[0];
        else if (retVal instanceof MetaResponse) {
            allSet = true;
            final MetaResponse mr = (MetaResponse) retVal;
            ret.put("body", mr.getBody());
            ret.put("status", mr.getResponseCode());
            ret.put("headers", mr.getHeaders());
        } else if (retVal instanceof Node && null == instSerializer) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource((Node) retVal);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(bos);
            transformer.transform(source, result);
            retVal = bos.toByteArray();
            contentType = "text/xml";
        } else if (retVal instanceof byte[] ||
                retVal instanceof OutputStream) {
            ret.put("body", retVal);
        } else {
            Function<Object, byte[]> contextSerializer =
                    runOperation("getSerializer",
                            altResponseInfo, logger, Function.class);
            if (null != instSerializer) {
                retVal = instSerializer.apply(retVal);
            } else if (null != contextSerializer) {
                retVal = contextSerializer.apply(retVal);
            } else {
                retVal = serializer.apply(retVal);
            }
        }

        String fct = null;

        if (null != altResponseInfo) {
            Object o = altResponseInfo.get("content-type");
            if (null != o && o instanceof String) {
                fct = (String) o;
            }
        }

        try {
            if (null != func) fct = func.contentType();
        } catch (UnsupportedOperationException e) {
            // ignore ... the implementation didn't get the Java default method... sigh
        }

        final String ct2 = fct == null ? contentType : fct;

        Map<String, Object> someHeaders = new HashMap<>();

        if (null != altResponseInfo) {
            Object o = altResponseInfo.get("headers");
            if (null != o && o instanceof Map) {
                someHeaders = (Map<String, Object>) o;
            }
        }

        try {
            if (null != func) someHeaders = func.headers();
        } catch (UnsupportedOperationException e) {
            // ignore ... the implementation didn't get the Java default method... sigh
        }

        int status = 200;

        if (null != altResponseInfo) {
            Object o = altResponseInfo.get("status");
            if (null != o && o instanceof Number) {
                status =  ((Number)o).intValue();
            }
        }

        try {
            if (null != func) status = func.statusCode();
        }  catch (UnsupportedOperationException e) {
            // ignore ... the implementation didn't get the Java default method... sigh
        }

        // we didnt' get a MetaResponse, so set the return value
        if (!allSet) {
            ret.put("status", status);
            HashMap<String, Object> headers = new HashMap<>(someHeaders);
            if (!headers.containsKey("content-type")) {
                headers.put("content-type", ct2);
            }
            ret.put("headers", headers);
            ret.put("body", retVal);
        }

        if (theContext instanceof Accumulator)
            ((Accumulator) theContext).finished(true);

        return ret;
    }


    private static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    static {
        String FEATURE = null;
        try {
            // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all XML entity attacks are prevented
            // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
            FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
            dbf.setFeature(FEATURE, true);

            // If you can't completely disable DTDs, then at least do the following:
            // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
            // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
            // JDK7+ - http://xml.org/sax/features/external-general-entities
            FEATURE = "http://xml.org/sax/features/external-general-entities";
            dbf.setFeature(FEATURE, false);

            // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
            // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
            // JDK7+ - http://xml.org/sax/features/external-parameter-entities
            FEATURE = "http://xml.org/sax/features/external-parameter-entities";
            dbf.setFeature(FEATURE, false);

            // Disable external DTDs as well
            FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
            dbf.setFeature(FEATURE, false);

            // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks" (see reference below)
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            // And, per Timothy Morgan: "If for some reason support for inline DOCTYPEs are a requirement, then
            // ensure the entity settings are disabled (as shown above) and beware that SSRF attacks
            // (http://cwe.mitre.org/data/definitions/918.html) and denial
            // of service attacks (such as billion laughs or decompression bombs via "jar:") are a risk."


        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize the XML parser", e);

        }
    }

    /**
     * Safely parse the XML
     *
     * @param is the inputstream
     * @return the parsed XML
     */
    public static Document documentFromInputStream(InputStream is) {
        if (null == is) return null;

        try {
            return dbf.newDocumentBuilder().parse(is);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse the XML", e);

        }
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

        path.forEach((k, v) -> ret.put(k, v));
        query.forEach((k, v) -> ret.put(k, v));

        return ret;
    }

    /**
     * Get the Swagger-coerced query parameters
     *
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
     * Return the Content-Type header value
     *
     * @return the content type
     */
    @Override
    public String contentType() {
        Map<String, Object> headers = this.getHeaders();
        if (null == headers) return null;
        Object tRet = null;

        if (headers.containsKey("Content-Type")) tRet = headers.get("Content-Type");
        else if (headers.containsKey("content-type")) tRet = headers.get("Content-Type");

        if (null != tRet && tRet instanceof List) {
            List tl = (List) tRet;
            if (tl.size() > 0) tRet = tl.get(0);
        }

        if (tRet instanceof String) return (String) tRet;
        return null;
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
     *
     * @return version information so the Runner can do the right thing
     */
    public static String getVersion(Logger logger) {
        InputStream is = contextClassloader.getResourceAsStream("META-INF/maven/funcatron/intf/pom.properties");
        try {
            if (null != is) {
                Properties p = new Properties();
                p.load(is);
                return p.getProperty("version");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e, () ->"Failed to get version");
        }
        return "?";
    }

    @Override
    public <T> void accumulate(T item, ServiceVendor<T> vendor) {
        getLogger().log(Level.FINER, () -> "Accumulating " + item);
        toTerminate.add(new ReleasePair(item, vendor));
    }

    public void finished(boolean success) {
        getLogger().log(Level.FINER, () -> "Finished " + success + " Notifying " + toTerminate.size());
        toTerminate.forEach(a -> {
                    // cast to something to avoid type error
                    ReleasePair<Object> b = (ReleasePair<Object>) a;
                    try {
                        getLogger().log(Level.FINER, () -> "Releasing " + b.item);
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
