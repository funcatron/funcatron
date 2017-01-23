package funcatron.service.spring_boot;

import funcatron.intf.Constants;
import funcatron.intf.OperationProvider;
import funcatron.intf.impl.ContextImpl;
import io.swagger.models.Swagger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.AnnotationConfigWebContextLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import springfox.documentation.service.Documentation;
import springfox.documentation.spring.web.DocumentationCache;
import springfox.documentation.spring.web.json.JsonSerializer;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.mappers.ServiceModelToSwagger2Mapper;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In your application, subclass this class and implement the classList method.
 */
public abstract class SpringBootWrapper implements OperationProvider {

    /**
     * Return a list of the classes that Spring Boot needs to know about to boot the app
     *
     * @return the list of classes Spring Boot needs to know about... the list you'd
     * pass to {@code Application.run()} in your Spring Boot app.
     */
    public abstract Class<?>[] classList();

    private WebApplicationContext applicationContext;

    private static class SwaggerFinder {
        @Autowired
        private DocumentationCache documentationCache;

        @Autowired
        private ServiceModelToSwagger2Mapper mapper;

        @Autowired
        private JsonSerializer jsonSerializer;

        public String dumpSwagger() {
            try {
                String groupName = Docket.DEFAULT_GROUP_NAME;
                Documentation documentation = documentationCache.documentationByGroup(groupName);
                if (documentation == null) {
                    return null;
                }
                Swagger swagger = mapper.mapDocumentation(documentation);

                return jsonSerializer.toJson(swagger).value();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Failed to dump the Swagger", e);
            }
        }

    }

    public WebApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void autowire(Object o) {
        applicationContext.getAutowireCapableBeanFactory().autowireBean(o);
    }

    protected MockMvc getMockMvc() {
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(applicationContext);
return builder.build();
    }

    private Map<Object, Object> serviceRequest(InputStream is, Map<Object, Object> req) {
        Logger logger = (Logger) req.get("$logger");

        MockHttpServletRequestBuilder mockR;

        try {
            String method = (String) req.get("request-method");
            String uri = (String) req.get("uri");

            switch (method) {
                case "get":
                    mockR = MockMvcRequestBuilders.get(uri);
                    break;

                case "put":
                    mockR = MockMvcRequestBuilders.put(uri);
                    break;

                case "post":
                    mockR = MockMvcRequestBuilders.post(uri);
                    break;

                case "delete":
                    mockR = MockMvcRequestBuilders.delete(uri);
                    break;

                case "patch":
                    mockR = MockMvcRequestBuilders.patch(uri);
                    break;


                case "head":
                    mockR = MockMvcRequestBuilders.patch(uri);
                    break;
                default:
                    throw new RuntimeException("Unable to process HTTP request method '" + method + "'");
            }

            Map<String, Object> headers = (Map<String, Object>) req.get("headers");

            // set headers
            for (String k : headers.keySet()) {
                Object v = headers.get(k);
                if (null != v) {
                    if (v instanceof String) {
                        mockR.header(k, v);
                    } else if (v instanceof List) {
                        List<String> sl = (List<String>) v;
                        int sz = sl.size();
                        String[] sa = new String[sz];
                        for (int x = 0; x < sz; x++) {
                            sa[x] = sl.get(x);
                        }
                        mockR.header(k, sa);
                    }
                }
            }

            // FIXME do query params

            // deal with body
            if (null != is) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] ba = new byte[4096];
                int cnt;
                do {
                    cnt = is.read(ba);
                    if (cnt > 0) {
                        bos.write(ba, 0, cnt);
                    }
                } while (cnt >= 0);

                mockR.content(bos.toByteArray());
            }

            MockMvc router = getMockMvc();

            MockHttpServletResponse ra = router.perform(mockR).andReturn().getResponse();



            HashMap<Object, Object> ret = new HashMap<>();

            HashMap<String, Object> respHeaders = new HashMap<>();

            for (String k : ra.getHeaderNames()) {
                Object hv = ra.getHeaderValues(k);
                if (null != hv) {

                    respHeaders.put(k, hv);
                }
            }

            ret.put("status", ra.getStatus());
            ret.put("headers", respHeaders);
            ret.put("body", ra.getContentAsByteArray());

            return ret;
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Failed to service request", e);
        }
    }

    private BiFunction<InputStream, Map<Object, Object>, Map<Object, Object>> metaService(Map<Object, Object> params, Logger logger) {
        return this::serviceRequest;
    }

    @Override
    public void installOperation(BiFunction<String, BiFunction<Map<Object, Object>, Logger, Object>, Void> addOperation,
                                 Function<String, BiFunction<Map<Object, Object>, Logger, Object>> findOperation,
                                 Function<Function<Logger, Void>, Void> function1, ClassLoader classLoader, Logger logger) {

        BiFunction<Void, Logger, Void> originalEndLife =
                Constants.ConvertEndLifeFunc.apply(findOperation.apply(Constants.EndLifeConst));

        SpringApplication sa = new SpringApplication(classList());

        WebApplicationContext ac = (WebApplicationContext) sa.run();

        this.applicationContext = ac;

        addOperation.apply(Constants.GetSwaggerConst, (x, logger2) -> {
            HashMap<Object, Object> ret = new HashMap<>();

            ret.put("type", "json");
            SwaggerFinder gc = new SwaggerFinder();
            autowire(gc);

            ret.put("swagger", gc.dumpSwagger());

            return ret;
        });

        addOperation.apply(Constants.DispatcherForConst, (info, logger2) -> metaService(info, logger2));


        addOperation.apply(Constants.EndLifeConst, (x, logger2) -> {
            ((ConfigurableApplicationContext) ac).close();
            originalEndLife.apply(null, logger2);
            return null;
        });


    }
}
