package funcatron.java_sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import funcatron.intf.Context;
import funcatron.intf.Func;
import funcatron.intf.MetaResponse;

import javax.annotation.Resource;
import java.io.InputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A class that handles POST or DELETE
 */
public class PostOrDelete implements Func<Data> {

    private static final ObjectMapper jackson = new ObjectMapper();

    private
    @Resource
    Connection db;

    @Override
    public Object apply(Data data, Context context) {
        Number cnt = (Number) context.getRequestParams().get("path").get("cnt");
        if ("delete".equals(context.getMethod())) {
            return new Data("Deleted " + cnt.longValue(), cnt.intValue());
        } else if ("post".equals(context.getMethod())) {
            List<Data> ret = new ArrayList<>();
            for (int i = 1; i <= cnt.intValue(); i++) {
                ret.add(new Data(data.getName() + i, data.getAge() + i));
            }

            return ret;
        } else {
            return new MetaResponse() {

                @Override
                public int getResponseCode() {
                    return 400;
                }

                public String getContentType() {
                    return "text/plain";
                }

                @Override
                public byte[] getBody() {
                    return ("Expecting a POST or DELETE, but got " + context.getMethod()).getBytes();
                }
            };
        }
    }

    /**
     * A function that will take the JSON data in a Map and convert it
     * into a Request object. This is a good place to set up a Jackson
     * parser with custom Jackson stuff rather than relying on the bare
     * bones Jackson parser in the Runner.
     *
     * @return a customer JSON to Object parser or null if Funcatron should use the default Jackson implementation
     */
    @Override
    public Function<InputStream, Data> jsonDecoder() {
        return m -> {
            try {
                return jackson.readValue(m, Data.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize", e);
            }
        };
    }
}
