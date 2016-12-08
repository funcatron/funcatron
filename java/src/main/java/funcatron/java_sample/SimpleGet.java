package funcatron.java_sample;

import funcatron.intf.Context;
import funcatron.intf.Func;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

/**
 * Returns a simple Map
 */
public class SimpleGet implements Func<Object> {

    public Map<String, Object> apply(Object o, Context c) {

        c.getLogger().info("In Simple Get...");

        // create the return value
        Map<String, Object> ret = new HashMap<>();

        // get the optional num param
        Object num = c.getMergedParams().get("num");

        // if we've got one, put it in the 'num-param' field
        if (null != num) {
            ret.put("num-param", num);
        }

        // populate a bunch of other values
        ret.put("query-params", c.getRequestInfo().get("query-params"));
        ret.put("time", (new Date()).toString());
        ret.put("bools",true);
        ret.put("numero", (new Random()).nextDouble());

        c.getLogger().log(Level.INFO, "Returning", ret);

        // return the map which will be turned into a JSON blob
        return ret;
    }

}
