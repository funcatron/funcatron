package funcatron.abstractions;

import java.util.Map;

/**
 * Something that has a lifecycle
 */
public interface Lifecycle {
    /**
     * Start the thing
     * @throws Exception exceptions may be thrown if the thing fails to start
     */
    void startLife() throws Exception;

    /**
     * Disconnects the thing from whatever it's talking to and releases all resources
     * @throws Exception on failure to end
     */
    void endLife() throws Exception;


    /**
     * An implementation-specific set of information
     * @return
     */
    Map<Object, Object> allInfo();
}
