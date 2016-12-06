package funcatron.intf;

/**
 * Accumulate vended resources
 */
public interface Accumulator {
    <T> void accumulate(T item, ServiceVendor<T> vendor);
}
