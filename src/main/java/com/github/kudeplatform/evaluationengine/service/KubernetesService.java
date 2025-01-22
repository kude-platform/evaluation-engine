package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.GitEvaluationTask;
import com.google.gson.reflect.TypeToken;
import com.marcnuri.helm.Helm;
import com.marcnuri.helm.InstallCommand;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author timo.buechert
 */
@Service
@Slf4j
public class KubernetesService implements OrchestrationService {

    @Autowired
    private CoreV1Api coreV1Api;

    @Autowired
    private BatchV1Api batchV1Api;

    @Autowired
    private ApiClient apiClient;

    @Value("${EVALUATION_ENGINE_HOST:#{null}}")
    private String evaluationEngineHost;

    @Value("${EVALUATION_ENGINE_PORT:8080}")
    private String evaluationEnginePort;

    @Value("${NODES_RESERVED_FOR_SYSTEM:1}")
    private int nodesReservedForSystem = 1;


    @PostConstruct
    public void deleteAllTasks() throws IOException {
        Helm.list().call()
                .stream()
                .filter(release -> release.getName().startsWith("ddm-akka-"))
                .forEach(release -> Helm.uninstall(release.getName()).call());

        if (!StringUtils.hasText(evaluationEngineHost)) {
            evaluationEngineHost = getIpAdress();
        }
    }

    private String getIpAdress() throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("google.com", 80));
            return socket.getLocalAddress().getHostAddress();
        }
    }

    public int getNumberOfNodes() throws ApiException {
        return coreV1Api.listNode().execute().getItems().size() - nodesReservedForSystem;
    }

    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 30000))
    public void deployTask(final GitEvaluationTask gitEvaluationTask, final SettingsService settingsService,
                           final boolean multipleJobsPerNode) {
        final InstallCommand installCommand = new Helm(Paths.get("helm", "ddm-akka"))
                .install().withName(getName(gitEvaluationTask.taskId()))
                .set("name", getName(gitEvaluationTask.taskId()))
                .set("evaluationEngineHost", evaluationEngineHost)
                .set("evaluationEnginePort", evaluationEnginePort)
                .set("multipleJobsPerNode", multipleJobsPerNode)
                .set("gitUrl", gitEvaluationTask.repositoryUrl())
                .set("replicaCount", settingsService.getReplicationFactor())
                .set("timeoutInSeconds", settingsService.getTimeoutInSeconds())
                .set("evaluationImage", settingsService.getEvaluationImage())
                .set("cpuRequest", settingsService.getCpuRequest())
                .set("cpuLimit", settingsService.getCpuLimit())
                .set("memoryRequest", settingsService.getMemoryRequest())
                .set("memoryLimit", settingsService.getMemoryLimit())
                .set("evaluationId", gitEvaluationTask.taskId())
                .set("datasetName", gitEvaluationTask.datasetName());

        addStartCommands(installCommand, gitEvaluationTask.instanceStartCommands());

        if (StringUtils.hasText(gitEvaluationTask.gitBranch())) {
            installCommand.set("gitBranch", gitEvaluationTask.gitBranch());
        }

        installCommand.call();
    }

    public String getName(final String taskId) {
        return String.format("ddm-akka-%s", taskId);
    }

    private void addStartCommands(final InstallCommand installCommand, final List<String> instanceStartCommands) {
        for (int i = 0; i < instanceStartCommands.size(); i++) {
            final String key = String.format("startCommands.START_COMMAND_%d", i);
            installCommand.set(key, instanceStartCommands.get(i));
        }
    }

    @Retryable(maxAttempts = 5, backoff = @Backoff(delay = 30000))
    public void deleteTask(String taskId) {
        final String name = String.format("ddm-akka-%s", taskId);
        Helm.list().call()
                .stream()
                .filter(release -> release.getName().equals(name))
                .forEach(release -> Helm.uninstall(name).call());
    }

    public KubernetesStatus waitForJobRunning(final String taskId, final int replicas) throws ApiException {
        return waitForJobStatus(taskId, replicas, KubernetesStatus::isRunning);
    }

    public KubernetesStatus waitForJobCompletion(final String taskId, final int replicas) throws ApiException {
        return waitForJobStatus(taskId, replicas, KubernetesStatus::isFinal);
    }

    public KubernetesStatus getJobStatus(final String taskId, final int replicas) throws ApiException {
        final V1Job job = batchV1Api
                .listNamespacedJob("evaluation")
                .fieldSelector(String.format("metadata.name=ddm-akka-%s", taskId))
                .execute()
                .getItems()
                .get(0);

        log.debug("Evaluating job status for evaluation id {} {}", taskId, Optional.ofNullable(job).map(V1Job::getStatus).map(V1JobStatus::toJson).orElse(""));

        return evaluateJobStatus(job, replicas);
    }

    public KubernetesStatus waitForJobStatus(final String taskId, final int replicas, final Function<KubernetesStatus, Boolean> jobStatusEvaluator) throws ApiException {
        while (true) {
            log.debug("Waiting for job status for evaluation id {}", taskId);
            final BatchV1Api.APIlistNamespacedJobRequest jobRequest = batchV1Api
                    .listNamespacedJob("evaluation")
                    .fieldSelector(String.format("metadata.name=ddm-akka-%s", taskId));

            final V1JobList initialJobList = jobRequest.execute();

            final KubernetesStatus kubernetesStatus = evaluateJobStatus(initialJobList.getItems().get(0), replicas);
            if (jobStatusEvaluator.apply(kubernetesStatus)) {
                return kubernetesStatus;
            }

            final Call jobCall = jobRequest.watch(true).buildCall(null);
            final Type type = new TypeToken<Watch.Response<V1Job>>() {
            }.getType();

            try (Watch<V1Job> watch = Watch.createWatch(apiClient, jobCall, type)) {
                for (Watch.Response<V1Job> item : watch) {
                    log.debug("Job status for task id {} {}", taskId, item.object.getStatus());
                    final KubernetesStatus status = evaluateJobStatus(item.object, replicas);
                    if (jobStatusEvaluator.apply(status)) {
                        final CoreV1Api.APIlistNamespacedPodRequest podRequest = coreV1Api
                                .listNamespacedPod("evaluation");
                        final List<V1Pod> podList = podRequest.execute()
                                .getItems().stream().filter(v1Pod -> v1Pod.getMetadata().getName().startsWith("ddm-akka-" + taskId)).toList();
                        for (V1Pod pod : podList) {
                            log.debug("Pod status for task id {} {}", taskId, Optional.ofNullable(pod).map(V1Pod::getStatus).map(V1PodStatus::toJson).orElse(""));
                        }

                        return status;
                    }
                }

            } catch (Exception e) {
                log.error("Error while waiting for job status", e);
                throw new RuntimeException(e);
            }
        }
    }

    public ReasonedKubernetesStatus getPodStatusOncePodsAreRunningOrWaiting(final String taskId, final int replicationFactor) throws ApiException {
        final CoreV1Api.APIlistNamespacedPodRequest podRequest = coreV1Api
                .listNamespacedPod("evaluation");

        final List<V1Pod> initialPodList = podRequest.execute()
                .getItems().stream().filter(v1Pod -> v1Pod.getMetadata().getName().startsWith("ddm-akka-" + taskId)).toList();

        final ReasonedKubernetesStatus reasonedKubernetesStatus = getPodStatusOncePodsAreRunningOrWaiting(initialPodList);

        if (reasonedKubernetesStatus.status().isRunning() || reasonedKubernetesStatus.status().isFinal()) {
            return reasonedKubernetesStatus;
        }

        final Call jobCall = podRequest.watch(true).buildCall(null);
        final Type type = new TypeToken<Watch.Response<V1Pod>>() {
        }.getType();

        final Map<String, Boolean> podStatus = new HashMap<>();
        try (Watch<V1Pod> watch = Watch.createWatch(apiClient, jobCall, type)) {

            for (Watch.Response<V1Pod> item : watch) {
                final ReasonedKubernetesStatus reasonedKubernetesStatusWatch = getPodStatusOncePodsAreRunningOrWaiting(List.of(item.object));

                if (reasonedKubernetesStatusWatch.status().isRunning() || reasonedKubernetesStatusWatch.status().isFinal()) {
                    podStatus.put(item.object.getMetadata().getName(), true);
                }
                if (podStatus.size() == replicationFactor && podStatus.values().stream().allMatch(Boolean::booleanValue)) {
                    return reasonedKubernetesStatusWatch;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new ReasonedKubernetesStatus(KubernetesStatus.UNKNOWN, null);
    }

    private ReasonedKubernetesStatus getPodStatusOncePodsAreRunningOrWaiting(final List<V1Pod> podList) {
        if (podList.stream().allMatch(v1Pod -> Optional.ofNullable(v1Pod.getStatus()).map(V1PodStatus::getPhase).orElse("").equals("Running"))) {
            return new ReasonedKubernetesStatus(KubernetesStatus.RUNNING, null);
        }

        for (int i = 0; i < podList.size(); i++) {
            final V1PodStatus status = podList.get(i).getStatus();
            for (int j = 0; j < Optional.ofNullable(status).map(V1PodStatus::getContainerStatuses).map(List::size).orElse(0); j++) {
                Optional<V1ContainerStateWaiting> containerStateWaiting =
                        Optional.ofNullable(status.getContainerStatuses().get(j)).map(V1ContainerStatus::getState).map(V1ContainerState::getWaiting);
                if (containerStateWaiting.isPresent() && containerStateWaiting.get().getReason() != null
                        && containerStateWaiting.get().getReason().equals("ImagePullBackOff")) { //TODO: reasons definieren und in liste packen
                    return new ReasonedKubernetesStatus(KubernetesStatus.FAILED, containerStateWaiting.get().getReason());
                }
            }
        }
        return new ReasonedKubernetesStatus(KubernetesStatus.PENDING, null);
    }

    private KubernetesStatus evaluateJobStatus(final V1Job v1Job, final int replicas) {
        final int running = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getActive).orElse(0);
        final int failed = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getFailed).orElse(0);
        final int succeeded = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getSucceeded).orElse(0);

        if (failed > 0) {
            return KubernetesStatus.FAILED;
        } else if (running == 0 && succeeded == replicas) {
            return KubernetesStatus.SUCCEEDED;
        } else if (running == replicas) {
            return KubernetesStatus.RUNNING;
        } else if (running < replicas) {
            return KubernetesStatus.PENDING;
        } else {
            return KubernetesStatus.UNKNOWN;
        }
    }

    public record LogFile(String name, byte[] content) {
    }
}
