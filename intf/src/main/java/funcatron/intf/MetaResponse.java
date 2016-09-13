package funcatron.intf;

import java.util.Map;
import java.io.OutputStream;

/**
 * By default, a Func returns something that's converted into JSON using Jackson. However,
 * if you want more control over the response, return an instance of MetaResponse
 */
public interface MetaResponse {
    /**
     * What's the HTTP response code?
     *
     * @return the HTTP response code
     */
    int getResponseCode();

    /**
     * Return all the headers
     *
     * @return key/value for the headers to send back
     */
    Map<String, String> getHeaders();

    /**
     * If the response is a large one (bigger than 20K or so), it'll be
     * inefficient to put the response on the response machinery... so return true
     * and the `writeBody` method will be invoked to write the response out of band
     * @return
     */
    boolean isLargeBody();

    /**
     * Return the response body. Called if `isLargeBody` is false.
     *
     * @return the bytes that make up the response
     */
    byte[] getBody();

    /**
     * Invoked if `isLargeBody` returns true. Write the response to the `OutputStream`
     *
     * @param out write the response body here
     */
    void writeBody(OutputStream out);

}
