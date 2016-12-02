package funcatron.java_sample;

import funcatron.intf.Context;
import funcatron.intf.Func;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Returns a simple Map
 */
public class SimpleGet implements Func<Object, Map<String, Object>> {

    public Map<String, Object> apply(Object o, Context c) {

        // create the return value
        Map<String, Object> ret = new HashMap<>();

        // get the optional num param
        Object num = c.getRequestParams().get("params").get("num");

        // if we've got one, put it in the 'num-param' field
        if (null != num) {
            ret.put("num-param", num);
        }

        // populate a bunch of other values
        ret.put("query-params", c.getRequestParams().get("query"));
        ret.put("time", (new Date()).toString());
        ret.put("bools",true);
        ret.put("numero", (new Random()).nextDouble());

        // return the map which will be turned into a JSON blob
        return ret;
    }

}
