package funcatron.sample.groovy

import funcatron.intf.Context
import funcatron.intf.Func
import funcatron.intf.MetaResponse
import java.util.logging.Level

/**
 * Created by dpp on 12/2/16.
 */
class Data {
    String name
    int age
    String toString() {"{"+name+", "+age+"}"}
}

/**
 * A class that handles POST or DELETE
 */
class PostOrDelete implements Func<Data> {
    @Override
    apply(Data data, Context context) {
        def cnt = (Number) context.getRequestParams().get("path").get("cnt")

        switch (context.getMethod()) {
            case "delete":
                return new Data(name: "Deleted " + cnt.longValue(),
                        age: cnt.intValue())


            case "post":
                return (1 .. cnt.intValue()).
                        collect({i -> new Data(name: data.name + i.toString(),
                        age: data.age + i)})

            default:
                return new MetaResponse() {

                    @Override
                    int getResponseCode() {
                        return 400
                    }

                    String getContentType() { return "text/plain" }

                    @Override
                    byte[] getBody() {
                        return ("Expecting a POST or DELETE, but got " + context.getMethod()).getBytes()
                    }
                }
        }
    }
}

/**
 * Returns a simple Map
 */
class SimpleGet implements Func<Object> {

    @Override
    apply(Object o, Context c) {

        c.getLogger().info("In Simple Get...")

        // get the optional num param
        def num = c.getMergedParams().get("num")

        // if we've got one, put it in the 'num-param' field
        def numMap = (null != num) ? ["num-param": num] : [:]

        // populate a bunch of other values
        def ret =
        ["query-params": c.getRequestInfo().get("query-params"),
         "time"        : (new Date()).toString(),
         "bools"       : true,
         "numero"      : (new Random()).nextDouble()] + numMap

        c.getLogger().log(Level.INFO, "Returning", ret)

        // return the map which will be turned into a JSON blob
        ret
    }

}

