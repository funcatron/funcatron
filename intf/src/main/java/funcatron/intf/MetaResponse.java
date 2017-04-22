package funcatron.intf;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.io.OutputStream;

/**
 * By default, a Func returns something that's converted into JSON using Jackson. However,
 * if you want more control over the response, return an instance of MetaResponse
 */
public interface MetaResponse extends Iterable<Map.Entry<String, Object>> {
    /**
     * What's the HTTP response code?
     *
     * @return the HTTP response code
     */
    default int getResponseCode() {
        return 200;
    }

    /**
     * Return all the headers
     *
     * @return key/value for the headers to send back
     */
    default Map<String, String> getHeaders() {
        return new HashMap<>();
    }

    default String getContentType() {
        return "application/octet-stream";
    }

    /**
     * If the response is a large one (bigger than 20K or so), it'll be
     * inefficient to put the response on the response machinery... so return true
     * and the `writeBody` method will be invoked to write the response out of band
     * @return boolean
     */
    default boolean isLargeBody() {
        return false;
    }

    /**
     * Return the response body. Called if `isLargeBody` is false.
     *
     * @return the bytes that make up the response
     */
    default byte[] getBody() {
        return null;
    }

    /**
     * Invoked if `isLargeBody` returns true. Return the OutputStream. Note
     * this method may be called more than one time and must always return the same
     * outputstream
     *
     * @return the output stream
     */
    default InputStream getBodyStream() {
        return null;
    }

    /**
     * Returns an Iterator over the named fields and values in the object
     *
     * @return an Iterator over the named fields and values in the object
     */
    default Iterator<Map.Entry<String, Object>> iterator() {
        HashMap<String, Object> ret = new HashMap<>();

        boolean large = isLargeBody();
        Object arrayBody = large ? null : getBody();
        Object streamBody = large ? getBodyStream() : null;
        Object body = large ? streamBody : arrayBody;

        ret.put("version", "1");
        ret.put("responseCode", getResponseCode());
        ret.put("headers", getHeaders());
        ret.put("contentType", getContentType());
        ret.put("largeBody", large);
        ret.put("bodyArray", arrayBody);
        ret.put("bodyStream", streamBody);
        ret.put("body", body);

        return ret.entrySet().iterator();
    }

}
