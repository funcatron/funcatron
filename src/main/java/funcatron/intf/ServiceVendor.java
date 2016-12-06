package funcatron.intf;

/**
 * A named vendor of a particular type of service
 */
public interface ServiceVendor<T> {

    /**
     * The name of the service
     * @return the name of the service
     */
    String name();

    /**
     * The type of the service vended
     *
     * @return the class object of the type of service vended
     */
    Class<T> type();


    /**
     * Is the class of a given type? For example `ofType(java.sql.Connection.class)`
     * to test for JDBC
     * @param clz
     * @return
     */
    default boolean ofType(Class<?> clz) {
        return type().isAssignableFrom(clz);
    }

    /**
     * Vends an instance of the thing (like a JDBC connection)
     *
     * @param acc the accumulator
     * @return an instance of the thing
     * @throws Exception failure to create the instance
     */
    T vend(Accumulator acc) throws Exception;

    /**
     * Close down any resources the vendor has (e.g., close all the JDBC connections)
     */
    void endLife();

    /**
     * Release the resource at the end of a Func execution. Depending on `success`,
     * the resource may be released in different ways (e.g., database commit vs. database rollback)
     * @param item the item to release
     * @param success
     * @throws Exception
     */
    void release(T item, boolean success) throws Exception;
}
