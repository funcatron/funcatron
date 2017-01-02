package funcatron.intf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import funcatron.intf.impl.Dispatcher;
import org.junit.Test;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * Tests the dispatch
 */
public class DispatchTest {
    final Dispatcher disp = new Dispatcher();

    final Map<Object, Object> basicParams = new HashMap<>();

    final ObjectMapper jackson = new ObjectMapper();

    public DispatchTest() {

        basicParams.put("$deserializer", new BiFunction<InputStream, Class<?>, Object>() {
            @Override
            public Object apply(InputStream inputStream, Class<?> aClass) {
                try {
                    return jackson.readValue(inputStream, aClass);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to serialize", e);
                }
            }
        });


        basicParams.put("$serializer", new Function<Object, byte[]>() {
            @Override
            public byte[] apply(Object o) {
                try {
                    return jackson.writeValueAsBytes(o);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize", e);
                }
            }
        });
    }

    InputStream toBytes(String s) throws IOException {
        return new ByteArrayInputStream(s.getBytes("UTF-8"));
    }

    InputStream toBytes(Map m) throws IOException {
        return new ByteArrayInputStream(jackson.writeValueAsBytes(m));
    }

    public static class Func1 implements Func<Object> {
        @Override
        public Object apply(Object req, Context context) throws Exception {
            return req;
        }
    }

    public static class Func2 implements Func<byte[]> {
        @Override
        public Object apply(byte[] req, Context context) throws Exception {
            return new String(req, "UTF-8");
        }
    }

    public static class Func3 implements Func<InputStream> {
        @Override
        public Object apply(InputStream req, Context context) throws Exception {
            return "Cnt="+req.read(new byte[1000]);
        }
    }

    public static class Func4 implements Func<Document> {
        @Override
        public Object apply(Document req, Context context) throws Exception {
            return req.getDocumentElement().getTagName();
        }
    }

    public static class Func5 implements Func<Document> {
        @Override
        public Object apply(Document req, Context context) throws Exception {
            return req;
        }
    }

    public static class Func6 implements Func<Document> {
        @Override
        public Object apply(Document req, Context context) throws Exception {
            return req;
        }

        @Override
        public Function<Object, byte[]> jsonEncoder() {
            return x -> "Hello".getBytes();
        }
    }

    @Test
    public void BasicDispatch() throws Exception {
        BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> invoke =
                disp.apply("funcatron.intf.DispatchTest$Func1", basicParams);

        Map<Object, Object> m = invoke.apply(toBytes("\"Hello\""), basicParams);
        assertEquals("\"Hello\"", new String((byte[]) m.get("body"), "UTF-8"));

        Map it = new HashMap() {{
            put("Name", "Archer");
            put("Age", 11);
        }};

        m = invoke.apply(toBytes( it), basicParams);
        assertEquals(it, jackson.readValue((byte[]) m.get("body"), Object.class));


        invoke = disp.apply("funcatron.intf.DispatchTest$Func2", basicParams);

        m = invoke.apply(toBytes("Hello"), basicParams);
        assertEquals("\"Hello\"", new String((byte[]) m.get("body"), "UTF-8"));

        invoke = disp.apply("funcatron.intf.DispatchTest$Func3", basicParams);

        m = invoke.apply(toBytes("Hello"), basicParams);
        assertEquals("\"Cnt=5\"", new String((byte[]) m.get("body"), "UTF-8"));

        invoke = disp.apply("funcatron.intf.DispatchTest$Func4", basicParams);

        m = invoke.apply(toBytes("<foo cat='4'>dog</foo>"), basicParams);
        assertEquals("\"foo\"", new String((byte[]) m.get("body"), "UTF-8"));

        invoke = disp.apply("funcatron.intf.DispatchTest$Func5", basicParams);

        m = invoke.apply(toBytes("<foo cat='4'>dog</foo>"), basicParams);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><foo cat=\"4\">dog</foo>", new String((byte[]) m.get("body"), "UTF-8"));

        invoke = disp.apply("funcatron.intf.DispatchTest$Func6", basicParams);

        m = invoke.apply(toBytes("<foo cat='4'>dog</foo>"), basicParams);
        assertEquals("Hello", new String((byte[]) m.get("body"), "UTF-8"));
    }

}
