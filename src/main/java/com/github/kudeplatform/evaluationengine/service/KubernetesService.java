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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

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

    public List<InputStream> getLogs(String taskId) throws ApiException {
        final PodLogs logs = new PodLogs();
        final List<V1Pod> pods = coreV1Api
                .listNamespacedPod("evaluation")
                .execute()
                .getItems()
                .stream().filter(v1Pod -> v1Pod.getMetadata().getName().startsWith(String.format("ddm-akka-%s", taskId)))
                .toList();

        return pods.stream().map(v1Pod -> {
            try {
                final Call call =
                        coreV1Api.readNamespacedPodLog(
                                        v1Pod.getMetadata().getName(),
                                        v1Pod.getMetadata().getNamespace())
                                .container(v1Pod.getSpec().getContainers().get(0).getName())
                                .follow(false)
                                .pretty("false")
                                .previous(false)
                                .sinceSeconds(null)
                                .tailLines(null)
                                .timestamps(false)
                                .buildCall(null);

                return call.execute().body().byteStream();
            } catch (ApiException | IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
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

    public void deployTask(String taskId, int numberOfReplicas) {
        final String name = String.format("ddm-akka-%s", taskId);

        new Helm(Paths.get("helm", "ddm-akka"))
                .install().withName(name)
                .set("name", name)
                .call();
    }

    public void deployTask(String taskId, String gitUrl, int numberOfReplicas) {
        final String name = String.format("ddm-akka-%s", taskId);

        new Helm(Paths.get("helm", "ddm-akka"))
                .install().withName(name)
                .set("name", name)
                .set("gitUrl", gitUrl)
                .call();
    }

    public void deleteTask(String taskId) {
        final String name = String.format("ddm-akka-%s", taskId);
        Helm.uninstall(name).call();
    }

    public void waitForJobCompletion(String taskId) throws ApiException {
        final BatchV1Api.APIlistNamespacedJobRequest jobRequest = batchV1Api
                .listNamespacedJob("evaluation")
                .fieldSelector(String.format("metadata.name=ddm-akka-%s", taskId));

        final V1JobList initialJobList = jobRequest.execute();

        if (isJobFinished(initialJobList.getItems().get(0))) {
            return;
        }

        final Call jobCall = jobRequest.watch(true).buildCall(null);
        final Type type = new TypeToken<Watch.Response<V1Job>>() {
        }.getType();

        try (Watch<V1Job> watch = Watch.createWatch(apiClient, jobCall, type)) {

            for (Watch.Response<V1Job> item : watch) {
                if (isJobFinished(item.object)) {
                    break;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isJobFinished(V1Job v1Job) {
        final boolean isJobFailed = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getFailed).orElse(0) > 0;
        final boolean isJobSucceeded = Optional.ofNullable(v1Job).map(V1Job::getStatus).map(V1JobStatus::getSucceeded).orElse(0) > 0;

        return isJobFailed || isJobSucceeded;
    }
}
