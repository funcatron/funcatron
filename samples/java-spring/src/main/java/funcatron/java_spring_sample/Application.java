package funcatron.java_spring_sample;


import funcatron.devshim.Register;
import funcatron.intf.Constants;
import funcatron.intf.impl.ContextImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;


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


        // Now that we've got the app running... start the whole funcatron stuff
        ContextImpl.initContext(new HashMap<>(),
                Application.class.getClassLoader(),
                Logger.getAnonymousLogger());

        // Using SpringFox, get the Swagger
        Map swagger = ContextImpl.runOperation(Constants.GetSwaggerConst,
                new HashMap<>(),
                Logger.getAnonymousLogger(), Map.class);

        // write it to a temp file
        File tmpFile = File.createTempFile("funcatron_swagger_", ".txt");
        tmpFile.createNewFile();
        FileOutputStream fos = new FileOutputStream(tmpFile);
        fos.write(swagger.get("swagger").toString().getBytes("UTF-8"));
        fos.flush();
        fos.close();

        // delete the temp file on exit
        tmpFile.deleteOnExit();

        // register with the devshim
        Register.register(tmpFile);
    }
}
