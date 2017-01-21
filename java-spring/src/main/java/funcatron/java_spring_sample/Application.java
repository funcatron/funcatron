package funcatron.java_spring_sample;


import funcatron.intf.OperationProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ServiceLoader;

@SpringBootApplication
@EnableSwagger2
public class Application {

    public static void main(String[] args) throws Exception {

        System.out.println(ServiceLoader.load(OperationProvider.class));

        System.exit(1);

        SpringApplication sa = new SpringApplication(Application.class);

        ApplicationContext ac = sa.run(args);


    }
}
