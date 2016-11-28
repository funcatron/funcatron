package funcatron.abstractions;

import funcatron.helpers.Tuple2;
import funcatron.helpers.Tuple3;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * How the Tron talks to the Container substrate (Mesos, Kubernetes, Docker Swarm)
 * to start, stop, and monitor
 */
public interface ContainerSubstrate {

    class ServiceType {
        public final String dockerImage;
        public final double cpu;
        public final double memory;
        public final List<Tuple2<String, String>> env;

        public ServiceType(String dockerImage,
                           double cpu,
                           double memory,
                           List<Tuple2<String, String>> env) {
            this.dockerImage = dockerImage;
            this.env = env;
            this.cpu = cpu;
            this.memory = memory;
        }
    }
    /**
     * Get all the internal information for the substrate... the results depend on
     * the implementation. This is mostly for debugging purposes
     * @return
     */
    Map<Object, Object> allInfo();

    /**
     * Start a service
     *
     * @param type the type of service to start
     * @param monitor an optional function that will be called with updates about this service
     *
     * @return the UUID for the function
     *
     * @throws IOException if there's a problem talking to the substrate
     */
    UUID startService(ServiceType type,
                      Function<Tuple3<UUID, TaskState, Map<String, Object>>, Void> monitor)
        throws IOException;

    /**
     * Get the status of a task
     *
     * @param id the ID of the task
     * @return the TaskState as well as information about the task in a Map
     * @throws IOException if there's a problem
     */
    Tuple2<TaskState, Map<String, Object>> status(UUID id) throws IOException;

    /**
     * Stop the service
     * @param id the ID of the service
     * @param monitor a callback function to monitor the shutdown
     * @throws IOException
     */
    void stopService(UUID id, Function<Tuple3<UUID, TaskState, Map<String, Object>>, Void> monitor) throws IOException;

    /**
     * Disconnects the service
     *
     * @throws IOException if there's a problem talking to the underlying service
     */
    void disconnect() throws IOException;

    enum TaskState {Unknown, Starting, Running, RequestedStop, Stopping, Stopped}
}
