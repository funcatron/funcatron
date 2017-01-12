package funcatron.intf;

import java.io.InputStream;
import java.util.Map;
import java.util.function.BiFunction;

/**
 *
 */
public interface MiddlewareProvider extends OrderedProvider {
    BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> wrap(BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> function);
}
