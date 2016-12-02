package funcatron.java_sample;

import funcatron.intf.Context;
import funcatron.intf.Func;
import funcatron.intf.MetaResponse;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that handles POST or DELETE
 */
public class PostOrDelete implements Func<Data, Object> {
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
            return new MetaResponse(){

                @Override
                public int getResponseCode() {
                    return 400;
                }

                @Override
                public Map<String, String> getHeaders() {
                    return new HashMap<>();
                }

                @Override
                public boolean isLargeBody() {
                    return false;
                }

                @Override
                public byte[] getBody() {
                    return ("Expecting a POST or DELETE, but got " + context.getMethod()).getBytes();
                }

                @Override
                public void writeBody(OutputStream outputStream) {

                }
            };
        }
    }
}
