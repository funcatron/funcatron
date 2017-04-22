package funcatron.intf;

/**
 * Accumulate vended resources
 */
public interface Accumulator {
    /**
     * Accumulate vendors that need "finishing"
     * @param item the item that was vended
     * @param vendor the thing doing the vending
     * @param <T> the type of thing
     */
    <T> void accumulate(T item, ServiceVendor<T> vendor);

    /**
     * For all the vended things, finish them either successfully onsuccessfully.
     *
     * @param success was the operation a success (e.g., commit) or not (e.g., rollback)
     */
    void finished(boolean success);
}
