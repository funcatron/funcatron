package funcatron.intf;

/**
 * Providers that need to be executed in a particular order should extend this interface
 */
public interface OrderedProvider {
    /**
     * Return the order in which the Providers should be executed
     * @return the order in which the provider will be layered
     */
    default int order() {return 0;}
}
