package funcatron.java_spring_sample;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;


/**
 * Just a plain old Spring app... but make sure you include the {@code @EnableSwagger2}
 * annotation to hook up SpringFox and generate Swagger
 */
@SpringBootApplication
@EnableSwagger2
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication sa = new SpringApplication(Application.class);

        sa.run(args);
    }
}
