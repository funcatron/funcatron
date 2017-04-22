package $package;

import funcatron.intf.Accumulator;
import funcatron.intf.ServiceVendor;
import funcatron.intf.ServiceVendorProvider;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * An example of a provider so the Context can vend instances of
 * things like DB drivers, cache drivers, etc.
 */
public class RedisProvider implements ServiceVendorProvider {
    /**
     * What's the name of this driver?
     * @return the unique name
     */
    @Override
    public String forType() {
        return "redis";
    }

    /**
     * Some fancy null testing
     * @param o an object
     * @param clz a class to test
     * @param <T> the type of the class
     * @return null if o is not an instance of the class or null
     */
    private <T> T ofType(Object o, Class<T> clz) {
        if (null != o &&
                clz.isInstance(o)) return (T) o;
        return null;
    }

    /**
     * Build something that will vend the named service based on the property map
     * @param name the name of the item
     * @param properties the properties
     * @param logger if something needs logging
     * @return If the properties are valid, return a ServiceVendor that will do the right thing
     */
    @Override
    public Optional<ServiceVendor<?>> buildVendor(String name, Map<String, Object> properties, Logger logger) {
        final String host = ofType(properties.get("host"), String.class);

        if (null == host) return Optional.empty();

        return Optional.of(new ServiceVendor<Jedis>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Class<Jedis> type() {
                return Jedis.class;
            }

            @Override
            public Jedis vend(Accumulator acc) throws Exception {
                Jedis ret = new Jedis(host);
                // make sure we are notified of release
                acc.accumulate(ret, this);
                return ret;
            }

            @Override
            public void endLife() {

            }

            @Override
            public void release(Jedis item, boolean success) throws Exception {
                item.close();
            }
        });
    }
}
