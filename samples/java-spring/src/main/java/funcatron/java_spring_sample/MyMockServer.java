package funcatron.java_spring_sample;

import funcatron.service.spring_boot.MockServer;
import org.springframework.stereotype.Component;

/**
 * Register our own mock server. Once again, necessary to mock requests
 */
@Component
public class MyMockServer extends MockServer {
}
