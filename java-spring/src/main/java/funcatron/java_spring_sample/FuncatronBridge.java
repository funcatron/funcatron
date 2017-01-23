package funcatron.java_spring_sample;

import funcatron.service.spring_boot.SpringBootWrapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.AnnotationConfigWebContextLoader;

/**
 * Bridges Spring to Funcation
 */

public class FuncatronBridge extends SpringBootWrapper {

    public FuncatronBridge() {
        super();
    }

    @Override
    public Class<?>[] classList() {
        return new Class<?>[]{Application.class};
    }
}
