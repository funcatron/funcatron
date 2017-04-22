package funcatron.jvm_services.clojure;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import funcatron.intf.Func;
import funcatron.intf.OperationProvider;
import funcatron.intf.impl.ContextImpl;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Create
 */
public class InstallClojureDispatcher implements OperationProvider {
    /**
     * Install an operation.
     *
     * @param addOperation a function to add a named operation. A named function takes a map, logger, and returns something.
     *                     This function takes the name of the operation and the operation.
     * @param getOperation get a named operation, or null if the operation doesn't exist.
     * @param addEndOfLife Add a function at the end of life. If there's anything allocated by the installation of the
     *                     operation (like creating a JDBC pool), then the operation should be released by the
     *                     end of life function.
     * @param classLoader  the ClassLoader for the Func Bundle
     * @param logger       the Logger
     */
    @Override
    public void installOperation(BiFunction<String, BiFunction<Map<Object, Object>, Logger, Object>, Void> addOperation, Function<String, BiFunction<Map<Object, Object>, Logger, Object>> getOperation, Function<Function<Logger, Void>, Void> addEndOfLife, ClassLoader classLoader, Logger logger) {
        addOperation.apply("dispatcherFor", (Map<Object, Object> objectMap,Logger l) -> {
            final String className = (String) objectMap.get("$operationId");

            l.log(Level.INFO, () -> "Doing Clojure dispatch for " + className);


            String[] sa = className.split("#");
            final String cljPackage = sa[0];
            final String funcName = sa[1];

            final BiFunction<InputStream, Class<?>, Object> deserializer =
                    (BiFunction<InputStream, Class<?>, Object>) objectMap.get("$deserializer");

            final Function<Object, byte[]> serializer = (Function<Object, byte[]>) objectMap.get("$serializer");

            final Map<Object, Object> cleanMap = new HashMap<>(objectMap);

            objectMap.keySet().forEach(k -> {
                if (k instanceof String &&
                        ((String) k).startsWith("$")) cleanMap.remove(k);
            });

            final Class paramType = Object.class;


            final Function<InputStream, Object> basicDeserializer = (paramType.isAssignableFrom(byte[].class)) ?
                    is -> ContextImpl.toByteArray(is) :
                    paramType.isAssignableFrom(InputStream.class) ?
                            is -> is :
                            paramType.isAssignableFrom(Document.class) ?
                                    is -> ContextImpl.documentFromInputStream(is) :
                                    null;

            IFn require = Clojure.var("clojure.core", "require");
            require.invoke(Clojure.read(cljPackage));
            final IFn theFunc = Clojure.var(cljPackage, funcName);


            BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> handler = (InputStream inputStream, Map<Object, Object> om) -> {
                Logger theLogger = (Logger) om.get("$logger");
                if (null == theLogger) theLogger = Logger.getLogger(className);
                final ContextImpl theContext = new ContextImpl(om, theLogger);

                try {


                    Callable<Object> resolveParam = ContextImpl.buildParameterResolver(inputStream, null,
                            basicDeserializer, deserializer, paramType, theContext.contentType(), theLogger);

                    Object retVal = theFunc.invoke(resolveParam.call(), theContext);

                    return ContextImpl.responseObjectToResponseMap(null, retVal, theContext, serializer, new HashMap<>(), theLogger);

                } catch (RuntimeException re) {
                    theContext.finished(false);
                    throw re;
                } catch (Exception e) {
                    theContext.finished(false);
                    throw new RuntimeException("Failed to apply function", e);
                }
            };

            return ContextImpl.wrapWithMiddleware(handler);
        } );
    }

}
