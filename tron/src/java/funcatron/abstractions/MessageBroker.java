package funcatron.abstractions;

import funcatron.helpers.Tuple2;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An abstraction for message brokers. What does a message broker need to
 * do?
 */
public interface MessageBroker extends Closeable {
    /**
     * Is the instance connected to the message broker?
     *
     * @return true if there's a connection to the message broker
     */
    boolean isConnected();


    interface ReceivedMessage {
        /**
         * Metadata associated with the message including headers, etc.
         * @return the metadata associated with the message
         */
        Map<String, Object> metadata();

        /**
         * The content type of the message
         * @return the content type
         */
        String contentType();

        /**
         * The body of the message. It will likely be decoded into
         * a type associated with the content type: JSON -> Map,
         * XML -> Node, or maybe a byte[] or String.
         *
         * @return the Body of the message
         */
        Object body();

        /**
         * Return the raw bytes that make up the message body
         * @return the raw bytes that make up the message body
         */
        byte[] rawBody();

        /**
         * Acknowledge the message
         *
         * @throws IOException if there's a communication error
         */
        void ackMessage() throws IOException;

        /**
         * Nacks the message.
         *
         * @param reQueue should the message be re-queued
         *
         * @throws IOException
         */
        void nackMessage(boolean reQueue) throws IOException;

        /**
         * Returns the message broker associated with this message
         * @return the message broker
         */
        MessageBroker messageBroker();
    }

    /**
     * Listen to the named queue for messages or other events
     *
     * @param queueName the name of the queue
     * @param handler a Function that consumes the message
     * @return a Runnable that, when the `.run()` method is invoked, will stop listening the the queue
     *
     * @throws IOException if something goes pear-shaped
     */
    Runnable listenToQueue(String queueName, Function<ReceivedMessage, Void> handler) throws IOException;

    /**
     * Send a message to the named queue
     *
     * @param queueName the name of the queue to send messages to
     * @param metadata key/value pairs of metadata (e.g., headers)
     * @param message the message which will be appropriately serialized given its type
     * @throws IOException if something goes pear-shaped
     */
    void sendMessage(String queueName, Map<String, Object> metadata, Object message) throws IOException;

    /**
     * Get a list of all the listeners. It's a Pair of the queue name and the Runnable to cancel the listening
     * @return a list of all the listeners
     */
    List<Tuple2<String, Runnable>> listeners();

    /**
     * Close the connection to the message broker and shuts down all the listeners
     *
     * @throws IOException if there's a problem
     */
    void close() throws IOException;

    /**
     * How many messages in the queue?
     *
     * @param queueName the name of the queue to test
     * @return the number of items in the queue
     * @throws IOException if something went wonky
     */
    long queueDepth(String queueName) throws IOException;
}
