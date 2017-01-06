package funcatron.intf.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import funcatron.intf.Accumulator;
import funcatron.intf.ServiceVendor;
import funcatron.intf.ServiceVendorBuilder;

import java.sql.Connection;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The built in JDBC vendor
 */
public class JDBCServiceVendorBuilder implements ServiceVendorBuilder {
    /**
     * The string type that this Builder will build a vendor for.
     * Example "database"
     *
     * @return the name of the type this builder will build for
     */
    @Override
    public String forType() {
        return "database";
    }

    private static String mapToString(Map map) {
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = iter.next();
            sb.append(entry.getKey());
            sb.append('=').append('"');
            sb.append(entry.getValue());
            sb.append('"');
            if (iter.hasNext()) {
                sb.append(',').append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * If the 'type' field in the properties matches
     *
     * @param name       the name of the ServiceVendor
     * @param properties properties for the service vendor... for example the JDBC connection information
     * @param logger     log anything to the logger
     * @return If we can successfull build the service vendor, build it
     */
    @Override
    public Optional<ServiceVendor<?>> buildVendor(String name, Map<String, Object> properties, Logger logger) {
        Set<String> reserved = new HashSet<>();
        String[] ra = {"type", "url", "username", "password"};
        Collections.addAll(reserved, ra);
        Object url = properties.get("url");
        Object username = properties.get("username");
        Object password = properties.get("password");
        Object classname = properties.get("classname");

        if (null != url && url instanceof String) {
            if (null != classname && classname instanceof String) {
                try {
                    this.getClass().getClassLoader().loadClass((String) classname);
                } catch (ClassNotFoundException cnf) {
                    logger.log(Level.WARNING, cnf, () -> "Unable to load DB driver class "+classname);
                }
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl((String) url);
            if (null != username && username instanceof String) config.setUsername((String) username);
            if (null != password && password instanceof String) config.setPassword((String) password);
            properties.entrySet().stream().
                    filter(a -> !reserved.contains(a.getKey())).
                    filter(a -> a.getValue() instanceof String).
                    forEach(a -> config.addDataSourceProperty(a.getKey(), a.getValue()));
            HikariDataSource ds = new HikariDataSource(config);

            return Optional.of(
                    new ServiceVendor<Connection>() {
                        /**
                         * The name of the service
                         *
                         * @return the name of the service
                         */
                        @Override
                        public String name() {
                            return name;
                        }

                        /**
                         * The type of the service vended
                         *
                         * @return the class object of the type of service vended
                         */
                        @Override
                        public Class<Connection> type() {
                            return Connection.class;
                        }

                        /**
                         * Vends an instance of the thing (like a JDBC connection)
                         *
                         * @param acc the accumulator
                         * @return an instance of the thing
                         * @throws Exception failure to create the instance
                         */
                        @Override
                        public Connection vend(Accumulator acc) throws Exception {
                            final Connection ret = ds.getConnection();
                            ret.setAutoCommit(false);
                            // make sure we are notified of release
                            acc.accumulate(ret, this);
                            return ret;
                        }

                        /**
                         * Close down any resources the vendor has (e.g., close all the JDBC connections)
                         */
                        @Override
                        public void endLife() {
                            ds.close();
                        }

                        /**
                         * Release the resource at the end of a Func execution. Depending on `success`,
                         * the resource may be released in different ways (e.g., database commit vs. database rollback)
                         *
                         * @param item    the item to release
                         * @param success
                         * @throws Exception
                         */
                        @Override
                        public void release(Connection item, boolean success) throws Exception {
                            try {
                                if (success) item.commit();
                                else item.rollback();
                            } finally {
                                item.close();
                            }
                        }
                    }

            );

        }
        logger.warning("Trying to build database connection, but " + mapToString(properties) + " does not contain 'url' parameter");
        return Optional.empty();
    }
}
