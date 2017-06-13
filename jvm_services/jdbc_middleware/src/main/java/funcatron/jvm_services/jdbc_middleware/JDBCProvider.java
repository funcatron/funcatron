package funcatron.jvm_services.jdbc_middleware;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import funcatron.intf.MiddlewareProvider;
import funcatron.intf.impl.ContextImpl;

/**
 * A JDBC provider. Puts a JDBC Connection (from a pool) into the Context.getRequestInfo() map.
 * The Connection is set to autocommit = false. If the request completes correctly (no exception),
 * the Connection will be committed. If an exception is thrown, it'll be rolled back.
 * <p>
 * Set the follow properties when enabling the Func Bundle to pick up the JDBC information:
 * <ul>
 *     <li> `jdbc.url` -- The URL of the database</li>
 * <li>`jdbc.classname` -- the optional classname of the JDBC driver</li>
 * <li>`jdbc.username` -- the username of the DB</li>
 * <li>`jdbc.password` -- the password for the DB</li>
 * </ul>
 * </p>
 */
public class JDBCProvider implements MiddlewareProvider {

    private static final Object syncObj = new Object();

    private static boolean initialized = false;
    private static HikariDataSource pool = null;

    /**
     * The name of the entry in the Context.getRequestInfo() map where the Connection will be
     */
    public static final String dbConnectionName = "$jdbc_connection";

    /**
     * Get the connection from the map
     * @param info the Map from Contect.getRequestInfo()
     * @return a Connection if it's in the map
     */
    public static Connection connectionFromMap(Map<Object, Object> info) {
        Object ret = info.get(dbConnectionName);
        if (null != ret && ret instanceof Connection) {
            return (Connection) ret;
        }

        return null;
    }

    private static void shutdown() {
        synchronized (syncObj) {
            if (initialized && null != pool) {
                pool.close();
            }
        }
    }

    /**
     * Shut down the pool when the Func Bundle is unloaded
     */
    static {
        ContextImpl.addFunctionToEndOfLife((a) -> {
            shutdown();
            return null;
        });
    }

    /**
     * Get a value from the map and make sure it's a String
     * @param key the key to find
     * @param map the map
     * @return the value if it's a String cast to a String
     */
    protected static String fromMap(String key, Map<String, Object> map) {
        Object x = map.get(key);
        if (null != x && x instanceof String) {
            return (String) x;
        }

        return null;
    }

    /**
     * Obtain a connection. Initialize the pool if this is the first time through
     * @param logger the Logger
     * @return the Connection if the pool is set up correctly
     */
    protected static Connection _obtainConnection(Logger logger) {
        try {
            synchronized (syncObj) {
                if (!initialized) {
                    initialized = true;

                    Map<String, Object> contextProps = ContextImpl.staticProperties();

                    String dbUrl = fromMap("jdbc.url", contextProps);
                    if (null != dbUrl) {
                        Properties dbProps = new Properties();

                        dbProps.put("jdbcUrl", dbUrl);

                        String dbname = fromMap("jdbc.classname", contextProps);
                        if (null != dbname) {
                            dbProps.put("driverClassName", dbname);
                        }

                        dbProps.put("autoCommit", false);

                        String user = fromMap("jdbc.username", contextProps);
                        if (null != user) {
                            dbProps.put("username", user);
                        }

                        String pwd = fromMap("jdbc.password", contextProps);
                        if (null != pwd) {
                            dbProps.put("password", pwd);
                        }

                        HikariConfig config = new HikariConfig(dbProps);
                        pool = new HikariDataSource(config);
                    }
                }
            }

            if (null != pool) {
                return pool.getConnection();
            }
        } catch (SQLException sqe) {
            if (null != logger) {
                logger.log(Level.SEVERE, sqe, () -> "Catastrophic DB pool error");
            }
        }

        return null;
    }

    /**
     * The non-static version to obtain a connection. Override if you want to change the logic
     * @param logger the Logger
     * @return a Connection
     */
    protected Connection obtainConnection(Logger logger) {
        return _obtainConnection(logger);
    }

    /**
     * Wrap the operation in Middleware that gets a connection
     * @param biFunction the function that performs the operation
     * @return the value of the operation
     */
    @Override
    public BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> wrap(BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> biFunction) {
        return (is, info) -> {
            Logger logger = null;
            Object tl = info.get("$logger");
            if (null != tl && tl instanceof Logger) {
                logger = (Logger) tl;
            }

            Connection conn = obtainConnection(logger);
            try {
                try {

                    if (null != conn) {
                        info = new HashMap<>(info);
                        info.put(dbConnectionName, conn);
                    }
                    Map<Object, Object> ret = biFunction.apply(is, info);

                    if (null != conn) {
                        conn.commit();
                    }

                    return ret;
                } catch (SQLException se) {
                    throw new RuntimeException("Database Error", se);
                }
            } catch (RuntimeException re) {
                try {
                    if (null != conn) {
                        conn.rollback();
                    }
                } catch (SQLException e) {
                    if (null != logger) {
                        logger.log(Level.SEVERE, e, () -> "Failed to roll transaction back");
                    }
                }
                throw re;
            }
        };
    }
}
