package funcatron.service.spring_boot;

import funcatron.intf.Constants;
import funcatron.intf.OperationProvider;
import io.swagger.models.Swagger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import springfox.documentation.service.Documentation;
import springfox.documentation.spring.web.DocumentationCache;
import springfox.documentation.spring.web.json.JsonSerializer;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.mappers.ServiceModelToSwagger2Mapper;

import java.util.HashMap;
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
     * @return the list of classes Spring Boot needs to know about... the list you'd
     * pass to {@code Application.run()} in your Spring Boot app.
     */
    public abstract Class<?>[] classList();


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


    @Override
    public void installOperation(BiFunction<String, BiFunction<Map<Object, Object>, Logger, Object>, Void> addOperation,
                                 Function<String, BiFunction<Map<Object, Object>, Logger, Object>> findOperation,
                                 Function<Function<Logger, Void>, Void> function1, ClassLoader classLoader, Logger logger) {

        logger.log(Level.WARNING, () -> "In install Spring Boot... my class is "+this.getClass().getName());

         BiFunction<Void, Logger, Void> originalEndLife =
                 Constants.ConvertEndLifeFunc.apply(findOperation.apply(Constants.EndLifeConst));

        SpringApplication sa = new SpringApplication(classList());

        ApplicationContext ac = sa.run();

        addOperation.apply(Constants.GetSwaggerConst, (x, logger2) -> {
            HashMap<Object, Object> ret = new HashMap<>();

            ret.put("type", "json");
            SwaggerFinder gc = new SwaggerFinder();
            ac.getAutowireCapableBeanFactory().autowireBean(gc);

            ret.put("swagger", gc.dumpSwagger());

            return ret;
        });


        addOperation.apply(Constants.EndLifeConst, (x, logger2) -> {
            ((ConfigurableApplicationContext) ac).close();
            originalEndLife.apply(null, logger2);
            return null;
        });



    }
}
