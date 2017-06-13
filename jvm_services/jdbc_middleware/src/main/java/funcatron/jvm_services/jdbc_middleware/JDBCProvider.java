package funcatron.jvm_services.jdbc_middleware;

import funcatron.intf.ClassloaderProvider;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.BiFunction;
import funcatron.intf.MiddlewareProvider;
import funcatron.intf.impl.ContextImpl;

/**
 * Initialize the Clojure Context
 */
public class JDBCProvider implements MiddlewareProvider {
    static {
        ContextImpl.addFunctionToEndOfLife(null);
        ContextImpl.
    }
    private static Connection _obtainConnection() {
        return null;
    }

    protected Connection obtainConnection() {
        return null;
    }
    @Override
    public BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> wrap(BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> biFunction) {
        return (is, info) -> {
            Connection conn = obtainConnection();
            try {
                conn.setAutoCommit(false);
                Map<Object, Object> ret = biFunction.apply(is, info);

                conn.commit();

                return ret;
            } catch (SQLException se) {
                // FIXME ...
                return null;
            } catch (RuntimeException re) {
                try {
                    conn.rollback();
                } catch (Exception e) {
                    // FIXME log exception
                }
                throw re;
            }
        };
    }
}
