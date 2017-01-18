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
import java.util.concurrent.Callable;
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
     * Based on the name of a class and other information from the Swagger definition, return a function that takes the
     * input as well as other information, and invoke the Func
     *
     * @param className the OperationId from the Swagger file
     * @param objectMap the rest of the Swagger file plus some other stuff
     * @return <code>BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>></code>
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

            final Class<Func<Object>> c = (Class<Func<Object>>) this.getClass().getClassLoader().loadClass(className);

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
                    is -> ContextImpl.toByteArray(is) :
                    paramType.isAssignableFrom(InputStream.class) ?
                            is -> is :
                            paramType.isAssignableFrom(Document.class) ?
                                    is -> ContextImpl.documentFromInputStream(is) :
                                    null;


            return ContextImpl.makeResponseBiFunc(serializer, deserializer, basicDeserializer, className, paramType, c);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build dispatch function", e);
        }

    }
}
