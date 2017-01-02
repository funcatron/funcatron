package funcatron.intf.impl;

import funcatron.intf.Func;
import funcatron.intf.MetaResponse;
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
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Code to build all the pieces needed to dispatch a Func request.
 * It implemented BiFunction so that it can be accessed without reflection from
 * Funcatron.
 * <p>
 * Created by dpp on 12/31/16.
 */
public class Dispatcher implements BiFunction<String, Map<Object, Object>, BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>>> {

    /**
     * Slurp an input stream to a byte array... why this isn't part of the JDK is stupid
     *
     * @param is the inputstream
     * @return the resulting byte array
     */
    private static byte[] toByteArray(InputStream is) {
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
    private static Document documentFromInputStream(InputStream is) {
        if (null == is) return null;

        try {
            return dbf.newDocumentBuilder().parse(is);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse the XML", e);

        }
    }

    /**
     * Based on the name of a class and other information from the Swagger definition, return a function that takes the
     * input as well as other information, and invoke the Func
     *
     * @param className the OperationId from the Swagger file
     * @param objectMap the rest of the Swagger file plus some other stuff
     * @return
     */
    @Override
    public BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> apply(final String className, final Map<Object, Object> objectMap) {
        try {
            final BiFunction<InputStream, Class<?>, Object> deserializer =
                    (BiFunction<InputStream, Class<?>, Object>) objectMap.get("$deserializer");

            final Function<Object, byte[]> serializer = (Function<Object, byte[]>) objectMap.get("$serializer");

            final Map<Object, Object> cleanMap = new HashMap<>(objectMap);

            objectMap.keySet().forEach(k -> {
                if (k instanceof String &&
                        ((String) k).startsWith("$")) cleanMap.remove(k);
            });

            final Class<Func<?>> c = (Class<Func<?>>) this.getClass().getClassLoader().loadClass(className);

            final Class paramType =
                    Arrays.stream(c.getAnnotatedInterfaces()).
                            map(a -> a.getType()).
                            filter(a -> a instanceof ParameterizedType).
                            map(a -> (ParameterizedType) a).
                            filter(a -> a.getTypeName().startsWith("funcatron.intf.Func<")).
                            flatMap(a -> Arrays.stream(a.getActualTypeArguments())).
                            filter(a -> a instanceof Class<?>).
                            map(a -> (Class) a).
                            findFirst().orElse(Object.class);

            final Function<InputStream, Object> basicDeserializer = (paramType.isAssignableFrom(byte[].class)) ?
                    is -> toByteArray(is) :
                    paramType.isAssignableFrom(InputStream.class) ?
                            is -> is :
                            paramType.isAssignableFrom(Document.class) ?
                                    is -> documentFromInputStream(is) :
                                    null;


            return (InputStream inputStream, Map<Object, Object> om) -> {

                Logger theLogger = (Logger) om.get("$logger");
                if (null == theLogger) theLogger = Logger.getLogger(className);
                final ContextImpl theContext = new ContextImpl(om, theLogger);

                try {
                    final Func<Object> func = (Func<Object>) c.newInstance();

                    final HashMap<Object, Object> ret = new HashMap<>();
                    Object param = null;
                    if (null != inputStream) {
                        Function<InputStream, Object> instDeserializer = func.jsonDecoder();
                        if (null != instDeserializer) {
                            param = instDeserializer.apply(inputStream);
                        } else if (null != basicDeserializer) {
                            param = basicDeserializer.apply(inputStream);
                        } else {
                            param = deserializer.apply(inputStream, paramType);
                        }
                    }

                    Object retVal = func.apply(param, theContext);
                    boolean allSet = false;

                    final Function<Object, byte[]> instSerializer = func.jsonEncoder();

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

                        if (null != instSerializer) {
                            retVal = instSerializer.apply(retVal);
                        } else {
                            retVal = serializer.apply(retVal);
                        }
                    }

                    final String ct2 = func.contentType() == null ? contentType : func.contentType();

                    // we didnt' get a MetaResponse, so set the return value
                    if (!allSet) {
                        ret.put("status", 200);
                        HashMap<String, Object> headers = new HashMap<>(func.headers());
                        if (!headers.containsKey("content-type")) {
                            headers.put("content-type", ct2);
                        }
                        ret.put("headers", headers);
                        ret.put("body", retVal);
                    }

                    theContext.finished(true);

                    return ret;

                } catch (RuntimeException re) {
                    theContext.finished(false);
                    throw re;
                } catch (Exception e) {
                    theContext.finished(false);
                    throw new RuntimeException("Failed to apply function", e);
                }
            };
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build dispatch function", e);
        }

    }
}
