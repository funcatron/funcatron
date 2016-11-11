package funcatron.mesos;
/*
 *    Copyright (C) 2015 Mesosphere, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copied from the mesos rx codebase, but updated to be more generic
 */

import com.mesosphere.mesos.rx.java.MesosClient;
import funcatron.helpers.Tuple2;
import funcatron.helpers.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.stream.Collectors.groupingBy;

import com.mesosphere.mesos.rx.java.MesosClientBuilder;
import com.mesosphere.mesos.rx.java.SinkOperation;
import com.mesosphere.mesos.rx.java.SinkOperations;
import com.mesosphere.mesos.rx.java.protobuf.ProtoUtils;
import com.mesosphere.mesos.rx.java.protobuf.ProtobufMesosClientBuilder;
import com.mesosphere.mesos.rx.java.protobuf.SchedulerCalls;
import org.apache.mesos.v1.Protos;
import org.apache.mesos.v1.Protos.*;
import org.apache.mesos.v1.scheduler.Protos.Call;
import org.apache.mesos.v1.scheduler.Protos.Event;
import org.jetbrains.annotations.NotNull;
import rx.Observable;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mesosphere.mesos.rx.java.SinkOperations.sink;
import static com.mesosphere.mesos.rx.java.protobuf.SchedulerCalls.decline;
import static com.mesosphere.mesos.rx.java.protobuf.SchedulerCalls.subscribe;
import static rx.Observable.from;

/**
 * A relatively simple Mesos framework that launches {@code sleep $SLEEP_SECONDS} tasks for offers it receives.
 * This framework uses the Mesos HTTP Scheduler API.
 *
 * @see <a href="https://github.com/mesosphere/mesos-rxjava/blob/master/mesos-rxjava-example/mesos-rxjava-example-framework/src/main/java/com/mesosphere/mesos/rx/java/example/framework/sleepy/Sleepy.java">Sleepy.java</a>
 */
public final class MesosController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MesosController.class);

    public final class DesiredTask {
        public final double memory;
        public final double cpu;
        public final String role;
        public final UUID id;
        public final String image;
        public final List<Tuple2<String, String>> env;

        public DesiredTask(UUID id,String image, double memory, double cpu, String role, final List<Tuple2<String, String>> env) {
            this.id = id;
            this.image = image;
            this.memory = memory;
            this.cpu = cpu;
            this.role = role;
            this.env = env;
        }

        public TaskInfo createTaskInfo(AgentID agentID) {
            return taskFor(image, id, cpu, memory, env).setAgentId(agentID).build();
        }
    }

    public interface StateManager {
        default FrameworkID getFwId() {
            return FrameworkID.newBuilder().setValue("Funcatron-" + UUID.randomUUID()).build();
        }

        void update(UUID taskID, TaskStatus status, TaskState taskState);

        List<DesiredTask> currentDesiredTasks();

        List<Tuple3<AgentID, ExecutorID, UUID>> shutdownTasks();
    }

    public static MesosClient<Call, Event> buildClient(final URI mesosUri, final StateManager stateObject) {
        final MesosClientBuilder<Call, Event> clientBuilder = ProtobufMesosClientBuilder.schedulerUsingProtos()
                .mesosUri(mesosUri);

        final Call subscribeCall = subscribe(
                stateObject.getFwId(),
                Protos.FrameworkInfo.newBuilder()
                        .setId(stateObject.getFwId())
                        .setUser(Optional.ofNullable(System.getenv("user")).orElse("root")) // https://issues.apache.org/jira/browse/MESOS-3747
                        .setName("Funcatron")
                        .setFailoverTimeout(0)
                        .setRole("*")
                        .build()
        );

        clientBuilder
                .subscribe(subscribeCall)
                .processStream(unicastEvents -> {
                    final Observable<Event> events = unicastEvents.share();

                    final Observable<Optional<SinkOperation<Call>>> offerEvaluations = events
                            .filter(event -> event.getType() == Event.Type.OFFERS)
                            .flatMap(event -> from(event.getOffers().getOffersList()))
                            .map(offer -> handleOffer(offer, stateObject))
                            .map(Optional::of);

                    final Observable<Optional<SinkOperation<Call>>> updateStatusAck = events
                            .filter(event -> event.getType() == Event.Type.UPDATE && event.getUpdate().getStatus().hasUuid())
                            .doOnNext(event -> {
                                final TaskStatus status = event.getUpdate().getStatus();

                                stateObject.update(UUID.fromString(status.getTaskId().getValue()), status, status.getState());
                            })
                            .map(event -> {
                                final TaskStatus status = event.getUpdate().getStatus();
                                return SchedulerCalls.ackUpdate(stateObject.getFwId(),
                                        status.getUuid(), status.getAgentId(), status.getTaskId());
                            })
                            .map(SinkOperations::create)
                            .map(Optional::of);

                    final Observable<Optional<SinkOperation<Call>>> shutdownStatus = events.
                            take(1).
                            flatMap(event -> {
                                List<Tuple3<AgentID, ExecutorID, UUID>> toShut = stateObject.shutdownTasks();

                                Stream<SinkOperation<Call>> dogs = toShut.stream().map(ae -> sink(shutdownTasks(stateObject.getFwId(),
                                        ae._1(), ae._2()),
                                        () ->  toShut.forEach(ae2 -> stateObject.update(ae2._3(),
                                                null,
                                                TaskState.TASK_KILLING))));


                                return Observable.from(dogs.collect(Collectors.toList()));
                            }).map(Optional::of);

                    final Observable<Optional<SinkOperation<Call>>> errorLogger = events
                            .filter(event -> event.getType() == Event.Type.ERROR || (event.getType() == Event.Type.UPDATE && event.getUpdate().getStatus().getState() == TaskState.TASK_ERROR))
                            .doOnNext(e -> LOGGER.warn("Task Error: {}", ProtoUtils.protoToString(e)))
                            .map(e -> Optional.empty());

                    return offerEvaluations.mergeWith(updateStatusAck).mergeWith(errorLogger).mergeWith(shutdownStatus);
                });

        return clientBuilder.build();
    }

    @NotNull
    private static SinkOperation<Call> handleOffer(final Offer offer, final StateManager state) {
        final FrameworkID frameworkId = state.getFwId();
        final AgentID agentId = offer.getAgentId();
        final List<OfferID> ids = newArrayList(offer.getId());


        final Map<String, List<Resource>> resources = offer.getResourcesList()
                .stream()
                .collect(groupingBy(Resource::getName));
        final List<Resource> cpuList = resources.get("cpus");
        final List<Resource> memList = resources.get("mem");
        final List<DesiredTask> desired = state.currentDesiredTasks();

        if (null != desired &&
                !desired.isEmpty() &&
                cpuList != null && !cpuList.isEmpty()
                && memList != null && !memList.isEmpty()
                && cpuList.size() == memList.size()) {

            final List<TaskInfo> tasks = newArrayList();

            final HashSet<UUID> serviced = new HashSet<>();

            for (int i = 0; i < cpuList.size(); i++) {


                final Resource cpus = cpuList.get(i);
                final Resource mem = memList.get(i);
                double availableCpu = cpus.getScalar().getValue();
                double availableMem = mem.getScalar().getValue();
                for (DesiredTask dt : desired) {
                    final String desiredRole = dt.role;
                    if (!serviced.contains(dt.id) && desiredRole.equals(cpus.getRole()) && desiredRole.equals(mem.getRole())) {

                        final double cpusPerTask = dt.cpu;
                        final double memMbPerTask = dt.memory;
                        if (availableCpu >= cpusPerTask && availableMem >= memMbPerTask) {
                            availableCpu -= cpusPerTask;
                            availableMem -= memMbPerTask;
                            serviced.add(dt.id);
                            tasks.add(dt.createTaskInfo(agentId));
                        }
                    }
                }
            }

            if (!tasks.isEmpty()) {
                LOGGER.info("Launching {} tasks", tasks.size());
                return sink(
                        invokeTasks(frameworkId, ids, tasks),
                        () -> tasks.forEach(task ->
                                state.update(UUID.fromString(task.getTaskId().getValue()),
                                null,
                                TaskState.TASK_STAGING)),
                        (e) -> LOGGER.warn("", e)
                );
            } else {
                return sink(decline(frameworkId, ids));
            }
        } else {
            return sink(decline(frameworkId, ids));
        }
    }

    @NotNull
    private static Call invokeTasks(
            @NotNull final FrameworkID frameworkId,
            @NotNull final List<OfferID> offerIds,
            @NotNull final List<TaskInfo> tasks
    ) {
        return Call.newBuilder()
                .setFrameworkId(frameworkId)
                .setType(Call.Type.ACCEPT)
                .setAccept(
                        Call.Accept.newBuilder()
                                .addAllOfferIds(offerIds)
                                .addOperations(
                                        Offer.Operation.newBuilder()
                                                .setType(Offer.Operation.Type.LAUNCH)
                                                .setLaunch(
                                                        Offer.Operation.Launch.newBuilder()
                                                                .addAllTaskInfos(tasks)
                                                )
                                )
                )
                .build();
    }

    @NotNull
    private static Call shutdownTasks(
            @NotNull final FrameworkID frameworkId,
            @NotNull final AgentID agentID,
            @NotNull final ExecutorID executorID
            ) {
        return Call.newBuilder()
                .setFrameworkId(frameworkId)
                .setType(Call.Type.SHUTDOWN).setShutdown(
                        Call.Shutdown.newBuilder().setAgentId(agentID).setExecutorId(executorID).build()
                )
                .build();
    }

    public static TaskInfo.Builder taskFor(String imageName, UUID taskID, double cpus, double mem, List<Tuple2<String, String>> envVars) {
        ContainerInfo.DockerInfo.Builder dockerInfoBuilder = ContainerInfo.DockerInfo.newBuilder();

        dockerInfoBuilder.setImage(imageName);

        for (int i = 0; i < envVars.size(); i++) {
            Tuple2<String,String> var = envVars.get(i);
            dockerInfoBuilder.addParameters(i, Parameter.newBuilder().
                    setKey("--env").
                    setValue(var._1()+"='"+var._2()+"'").build());
        }

        ContainerInfo.Builder containerInfoBuilder = ContainerInfo.newBuilder();
        containerInfoBuilder.setType(ContainerInfo.Type.DOCKER);

        containerInfoBuilder.setDocker(dockerInfoBuilder.build());

        final TaskID tid = TaskID.newBuilder().setValue(taskID.toString()).build();
        return TaskInfo.newBuilder().
                setTaskId(tid).
                setContainer(containerInfoBuilder).
                setCommand(Protos.CommandInfo.newBuilder().setShell(false)).
                addResources(Resource.newBuilder()
                        .setName("cpus")
                        .setType(Value.Type.SCALAR)
                        .setScalar(Value.Scalar.newBuilder().setValue(cpus)))
                .addResources(Resource.newBuilder()
                        .setName("mem")
                        .setType(Value.Type.SCALAR)
                        .setScalar(Value.Scalar.newBuilder().setValue(mem))).
                setName(imageName+ ": " + tid.getValue());
    }

//    @NotNull
//    private static TaskInfo sleepTask(
//            @NotNull final AgentID agentId,
//            @NotNull final String taskId,
//            @NotNull final String cpusRole,
//            final double cpus,
//            @NotNull final String memRole,
//            final double mem
//    ) {
//        final String sleepSeconds = Optional.ofNullable(System.getenv("SLEEP_SECONDS")).orElse("15");
//        return TaskInfo.newBuilder()
//                .setName(taskId)
//                .setTaskId(
//                        TaskID.newBuilder()
//                                .setValue(taskId)
//                )
//                .setAgentId(agentId)
//                .setCommand(
//                        CommandInfo.newBuilder()
//                                .setEnvironment(Environment.newBuilder()
//                                        .addVariables(
//                                                Environment.Variable.newBuilder()
//                                                        .setName("SLEEP_SECONDS").setValue(sleepSeconds)
//                                        ))
//                                .setValue("env | sort && sleep $SLEEP_SECONDS")
//                )
//                .addResources(Resource.newBuilder()
//                        .setName("cpus")
//                        .setRole(cpusRole)
//                        .setType(Value.Type.SCALAR)
//                        .setScalar(Value.Scalar.newBuilder().setValue(cpus)))
//                .addResources(Resource.newBuilder()
//                        .setName("mem")
//                        .setRole(memRole)
//                        .setType(Value.Type.SCALAR)
//                        .setScalar(Value.Scalar.newBuilder().setValue(mem)))
//                .build();
//    }


}
