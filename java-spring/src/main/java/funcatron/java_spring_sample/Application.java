package funcatron.java_spring_sample;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication sa = new SpringApplication(Application.class);

        ApplicationContext ac = sa.run(args);


    }
}
