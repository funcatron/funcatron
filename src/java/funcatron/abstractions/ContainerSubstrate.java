package funcatron.abstractions;

import funcatron.helpers.Tuple2;

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
    /**
     * Get all the internal information for the substrate... the results depend on
     * the implementation. This is mostly for debugging purposes
     * @return
     */
    Object allInfo();

    void startService(String type,
                      UUID id,
                      List<Tuple2<String, String>> envVars,
                      Function<Tuple2<TaskState, Map<String, String>>, Void> monitor)
        throws IOException;

    Tuple2<TaskState, Map<String, String>> status(UUID id) throws IOException;

    void stopService(UUID id, Function<Tuple2<TaskState, Map<String, String>>, Void> monitor) throws IOException;

    enum TaskState {Requested, Unknown, Starting, Running, RequestedStop, Stopping, Stopped}
}
