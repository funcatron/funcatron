package funcatron.abstractions;

import java.io.IOException;
import java.util.Map;

/**
 * The thing that routes a message
 */
public interface Router {

    interface Message {
        String replyTo();
        String host();
        String uri();
        String scheme();
        String method();
        String contentType();
        Map<String, Object> metadata();
        Object body();
    }

    /**
     * Convert the message from the more generic one from the MessageBroker into
     * something that can be routed
     * @param message
     * @return
     */
    Message brokerMessageToRouterMessage(MessageBroker.ReceivedMessage message);

    /**
     * Route the message. This may cause the message to be queued to the next handler (Runner)
     * or route it to the handler Func.
     *
     * @param message the Message to route
     * @return the result of the Message application or void if this Router forwards the message
     */
    Object routeMessage(Message message) throws IOException;
}
