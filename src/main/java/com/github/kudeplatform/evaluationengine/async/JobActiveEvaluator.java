package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.domain.SingleEvaluationResult;
import com.github.kudeplatform.evaluationengine.service.KubernetesService;
import com.github.kudeplatform.evaluationengine.service.OrchestrationServiceException;
import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1JobStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * @author timo.buechert
 */
@Component
//@Qualifier("preconditionEvaluator")
public class JobActiveEvaluator extends SimpleEvaluator {

    @Autowired
    KubernetesService kubernetesService;

    @Autowired
    Gson gson;

    @Override
    public CompletableFuture<Result> evaluate(final EvaluationTask evaluationTask,
                                              final Consumer<EvaluationEvent> updateCallback) {
        return CompletableFuture.supplyAsync(() -> {
            final List<EvaluationEvent> results = new ArrayList<>();
            try {
                while (true) {
                    final V1JobStatus jobStatus = kubernetesService.getJobStatus(evaluationTask.taskId().toString());
                    if (jobStatus.getActive() != null && jobStatus.getActive() > 0) {
                        break;
                    }
                    if (jobStatus.getFailed() != null && jobStatus.getFailed() > 0) {
                        throw new RuntimeException("Job failed.");
                    }
                    final EvaluationEvent event = new EvaluationEvent(evaluationTask.taskId(),
                            ZonedDateTime.now(),
                            EvaluationStatus.RUNNING,
                            "Job is not active yet. Current status: " + jobStatus.getConditions());
                    results.add(event);
                    updateCallback.accept(event);

                    Thread.sleep(1000);
                }
            } catch (ApiException | OrchestrationServiceException | InterruptedException e) {
                final EvaluationEvent finalErrorResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(),
                        EvaluationStatus.FAILED, e.getMessage());
                results.add(finalErrorResult);
                updateCallback.accept(finalErrorResult);
                return new SingleEvaluationResult(evaluationTask,
                        EvaluationStatus.FAILED,
                        results);
            }

            final EvaluationEvent finalResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(),
                    EvaluationStatus.SUCCEEDED, "Evaluation finished. Job is now active.");
            results.add(finalResult);
            updateCallback.accept(finalResult);
            return new SingleEvaluationResult(evaluationTask,
                    EvaluationStatus.SUCCEEDED,
                    results);
        });
    }

}
