package funcatron.intf;

import funcatron.intf.impl.ContextImpl;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class FuncTest {
    Func<Map> theFunc = new Func<Map>() {

        /**
         * For each incoming request, a new `Func` instance is created and the `apply`
         * method is invoked.
         * <p>
         * If the `Request` type is `Object` or `Map`, then the JSON body
         * of the incoming request is passed directly to the method.
         * <p>
         * If the `Request` type is `String` or `byte[]`, then the raw request is passed in.
         * <p>
         * If the `Request` type is `Node` or `Document`, then the request body is parsed using a DOM XML parser
         * <p>
         * If the request method (found in the Context) is GET or DELETE, the `req` is null
         * <p>
         * Regardless of the declared return type, here's the translation:
         * byte[] or String -- passed back
         * Node or Document -- considered XML, serialized to bytes accordingly
         * MetaResponse -- the contents of this object will determine the response
         *
         * @param req     the incoming request. `null` if the request is a GET or DELETE
         * @param context
         * @return
         */
        @Override
        public Object apply(Map req, Context context) {
            Map myMap = new HashMap(req);
            myMap.put("Name", context.getRequestParams().get("query").get("name"));
            return myMap;
        }
    };

    private Map<Object, Object> buildContextMap() {
        return new HashMap<Object, Object>() {{
            put("uri", "/hello/world");

            put("scheme", "http");

            put("host", "localhost");

            put("method", "get");

            put("parameters", new HashMap<String, Map<String, Object>>(){{
                put("query", new HashMap<String, Object>(){{
                    put("name", "Archer");
                }});

                put("path", new HashMap<String, Object>(){{}});

            }});

        }};
    }

    private Context newContext() {
        return new ContextImpl(buildContextMap(), Logger.getLogger("test"));
    }

    @Test
    public void CallFunc() throws Exception {
        Map in = new HashMap() {{
            put("snark", 33);
        }};

        Map toTest = (Map) theFunc.apply(in, newContext());

        assertEquals(toTest, new HashMap(in) {{
            put("Name", "Archer");
        }});
    }
}