package funcatron.abstractions;

import java.util.Map;

/**
 * This interface defines a stable store for
 * small pieces of data (< 100K). The general idea
 * is that the Stable Store uses ZooKeeper as the place to
 * store data. But maybe it's etcd or some other place
 */
public interface StableStore {

    /**
     * Gets the value from the backing store or null if
     * the value is not found
     *
     * @param key the key -- shouldn't contain any / or other funky characters
     * @return the Map or null if the key isn't found
     */
    Map get(String key);

    /**
     * Puts a value in the backing store
     *
     * @param key the key
     * @param value the value to store
     */
    void put(String key, Map value);

    /**
     * Remove the key from backing store
     *
     * @param key the key to remove from backing store
     */
    void remove(String key);
}
