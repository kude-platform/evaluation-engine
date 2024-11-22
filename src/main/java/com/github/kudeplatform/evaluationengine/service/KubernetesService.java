package com.github.kudeplatform.evaluationengine.service;

import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.marcnuri.helm.Helm;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import okhttp3.Call;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author timo.buechert
 */
@Service
public class KubernetesService implements OrchestrationService {

    @Autowired
    private CoreV1Api coreV1Api;

    @Autowired
    private BatchV1Api batchV1Api;

    @Autowired
    private CustomObjectsApi customObjectsApi;

    @Autowired
    private ApiClient apiClient;


    @PostConstruct
    public void deleteAllTasks() {
        Helm.list().call()
                .stream()
                .filter(release -> release.getName().startsWith("ddm-akka-"))
                .forEach(release -> Helm.uninstall(release.getName()).call());
    }

    public int getNumberOfNodes() throws ApiException {
        //return 12;
        //TODO: this currently fails due to https://github.com/kubernetes-client/java/issues/3319
        return coreV1Api.listNode().execute().getItems().size();
    }

    public List<V1Pod> getEvaluationPods(String taskId) throws ApiException {
        return getPods(String.format("ddm-akka-%s", taskId));
    }

    public V1Pod getLogCollectorPod(final String nodeName) throws ApiException {
        return getPods("log-collector").stream().filter(v1Pod -> v1Pod.getSpec().getNodeName().equals(nodeName)).findFirst().orElse(null);
    }

    public List<V1Pod> getPods(String name) throws ApiException {
        return coreV1Api
                .listNamespacedPod("evaluation")
                .execute()
                .getItems()
                .stream().filter(v1Pod -> v1Pod.getMetadata().getName().startsWith(name))
                .toList();
    }

    public HashMap<String, List<LogFile>> getLogs(String taskId) throws OrchestrationServiceException, ApiException {
        final List<V1Pod> pods = getEvaluationPods(taskId);

        final HashMap<String, List<LogFile>> logs = new HashMap<>();
        for (final V1Pod v1Pod : pods) {
            try {
                final String podName = v1Pod.getMetadata().getName();
                final String nodeNameOfPod = v1Pod.getSpec().getNodeName();
                final V1Pod logCollectorPod = getLogCollectorPod(nodeNameOfPod);
                final String hostIP = logCollectorPod.getStatus().getHostIP();

                String xmlListOfFiles = RestClient.create().get()
                        .uri("http://" + hostIP + ":31179/pods/evaluation_" + podName + "_" + v1Pod.getMetadata().getUid() + "/ddm-akka/")
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (request, response) -> {
                            throw new RuntimeException(String.format("Failed to get logs %s", response));
                        }).body(String.class);

                //xmlListOfFiles looks like this: <pre><a href="0.log">0.log</a><a href="1.log">1.log</a></pre>
                Elements logFiles = Jsoup.parse(xmlListOfFiles).body().getElementsByTag("pre").get(0).getElementsByTag("a");

                final List<LogFile> logFileList = new ArrayList<>();

                for (int i = 0; i < logFiles.size(); i++) {
                    Element logFile = logFiles.get(i);
                    String logFileName = logFile.attr("href");

                    byte[] logBytes = RestClient.create().get()
                            .uri("http://" + hostIP + ":31179/pods/evaluation_" + podName + "_" + v1Pod.getMetadata().getUid() + "/ddm-akka/" + logFileName)
                            .retrieve()
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (request, response) -> {
                                throw new RuntimeException(String.format("Failed to get logs %s", response));
                            }).body(byte[].class);

                    logFileList.add(new LogFile(logFileName, logBytes));

                }

                logs.put(podName, logFileList);
            } catch (ApiException e) {
                throw new OrchestrationServiceException(e.getMessage());
            }
        }
        return logs;
    }

    public InputStream getLogInputStream(String taskId, String replicaId) throws ApiException, IOException, OrchestrationServiceException {
        final PodLogs logs = new PodLogs();
        final V1Pod pod = coreV1Api
                .listNamespacedPod("evaluation")
                .execute()
                .getItems()
                .stream().filter(v1Pod -> v1Pod.getMetadata().getName()
                        .startsWith(String.format("ddm-akka-%s-%s", taskId, replicaId)))
                .findFirst()
                .orElseThrow(() -> new PodNotFoundException("Pod not found"));

        return logs.streamNamespacedPodLog(pod);
    }

    public V1JobStatus getJobStatus(String taskId) throws ApiException, OrchestrationServiceException {
        final V1Job job = batchV1Api
                .listNamespacedJob("evaluation")
                .execute()
                .getItems()
                .stream().filter(v1Job -> v1Job.getMetadata().getName().startsWith(String.format("ddm-akka-%s", taskId)))
                .findFirst()
                .orElseThrow(() -> new JobNotFoundException("Job not found"));

        return job.getStatus();
    }

    public LinkedTreeMap<?, ?> getPendingWorkloads(String taskId) throws ApiException, OrchestrationServiceException {
        final Object result = customObjectsApi
                .getClusterCustomObject("visibility.kueue.x-k8s.io", "v1alpha1", "clusterqueues", "cluster-queue/pendingworkloads")
                .execute();

        if (result instanceof LinkedTreeMap) {
            return (LinkedTreeMap<?, ?>) result;
        }

        throw new OrchestrationServiceException("Unexpected result type");
    }

    public void deployTask(String taskId, String additionalCommandLineOptions, int numberOfReplicas, int timeoutInSeconds) {
        final String name = String.format("ddm-akka-%s", taskId);

        new Helm(Paths.get("helm", "ddm-akka"))
                .install().withName(name)
                .set("name", name)
                .set("additionalCommandLineOptions", additionalCommandLineOptions)
                .set("replicaCount", numberOfReplicas)
                .set("timeoutInSeconds", timeoutInSeconds)
                .set("evaluationId", taskId)
                .call();
    }

    public void deployTask(String taskId, String gitUrl, String additionalCommandLineOptions, int numberOfReplicas, int timeoutInSeconds) {
        final String name = String.format("ddm-akka-%s", taskId);

        new Helm(Paths.get("helm", "ddm-akka"))
                .install().withName(name)
                .set("name", name)
                .set("gitUrl", gitUrl)
                .set("additionalCommandLineOptions", additionalCommandLineOptions)
                .set("replicaCount", numberOfReplicas)
                .set("timeoutInSeconds", timeoutInSeconds)
                .set("evaluationId", taskId)
                .call();
    }

    public void deleteTask(String taskId) {
        final String name = String.format("ddm-akka-%s", taskId);
        Helm.list().call()
                .stream()
                .filter(release -> release.getName().equals(name))
                .forEach(release -> Helm.uninstall(name).call());
    }

    public KubernetesJobStatus waitForJobRunning(final String taskId, final int replicas) throws ApiException {
        return waitForJobStatus(taskId, replicas, KubernetesJobStatus::isRunning);
    }

    public KubernetesJobStatus waitForJobCompletion(final String taskId, final int replicas) throws ApiException {
        return waitForJobStatus(taskId, replicas, KubernetesJobStatus::isFinal);
    }

    public KubernetesJobStatus waitForJobStatus(final String taskId, final int replicas, final Function<KubernetesJobStatus, Boolean> jobStatusEvaluator) throws ApiException {
        final BatchV1Api.APIlistNamespacedJobRequest jobRequest = batchV1Api
                .listNamespacedJob("evaluation")
                .fieldSelector(String.format("metadata.name=ddm-akka-%s", taskId));

        final V1JobList initialJobList = jobRequest.execute();

        final KubernetesJobStatus kubernetesJobStatus = evaluateJobStatus(initialJobList.getItems().get(0), replicas);
        if (jobStatusEvaluator.apply(kubernetesJobStatus)) {
            return kubernetesJobStatus;
        }

        final Call jobCall = jobRequest.watch(true).buildCall(null);
        final Type type = new TypeToken<Watch.Response<V1Job>>() {
        }.getType();

        try (Watch<V1Job> watch = Watch.createWatch(apiClient, jobCall, type)) {

            for (Watch.Response<V1Job> item : watch) {
                final KubernetesJobStatus status = evaluateJobStatus(item.object, replicas);
                if (jobStatusEvaluator.apply(status)) {
                    return status;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return KubernetesJobStatus.UNKNOWN;
    }

    private KubernetesJobStatus evaluateJobStatus(final V1Job v1Job, final int replicas) {
        final int running = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getActive).orElse(0);
        final int failed = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getFailed).orElse(0);
        final int succeeded = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getSucceeded).orElse(0);

        if (failed > 0) {
            return KubernetesJobStatus.FAILED;
        } else if (running == 0 && succeeded == replicas) {
            return KubernetesJobStatus.SUCCEEDED;
        } else if (running == replicas) {
            return KubernetesJobStatus.RUNNING;
        } else if (running < replicas) {
            return KubernetesJobStatus.PENDING;
        } else {
            return KubernetesJobStatus.UNKNOWN;
        }
    }

    public record LogFile(String name, byte[] content) {
    }
}
