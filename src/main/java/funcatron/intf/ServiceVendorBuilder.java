package funcatron.intf;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Build a service vendor based on type of the data
 */
public interface ServiceVendorBuilder {
    /**
     * The string type that this Builder will build a vendor for.
     * Example "database"
     * @return the name of the type this builder will build for
     */
    String forType();


    /**
     * If the 'type' field in the properties matches
     * @param name the name of the ServiceVendor
     * @param properties properties for the service vendor... for example the JDBC connection information
     * @param logger log anything to the logger
     * @return If we can successfull build the service vendor, build it
     */
    Optional<ServiceVendor<?>> buildVendor(String name, Map<String, Object> properties, Logger logger);
}
