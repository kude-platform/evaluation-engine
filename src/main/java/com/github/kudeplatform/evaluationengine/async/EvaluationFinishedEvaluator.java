package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.domain.SingleEvaluationResult;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.KubernetesService;
import com.github.kudeplatform.evaluationengine.service.KubernetesStatus;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Qualifier("parallelEvaluator")
public class EvaluationFinishedEvaluator extends SimpleEvaluator {

    @Autowired
    KubernetesService kubernetesService;

    @Autowired
    Gson gson;

    @Autowired
    SettingsService settingsService;

    @Autowired
    EvaluationResultRepository evaluationResultRepository;


    @Override
    public CompletableFuture<Result> evaluate(final EvaluationTask evaluationTask,
                                              final Consumer<EvaluationEvent> updateCallback) {
        return CompletableFuture.supplyAsync(() -> {
            final List<EvaluationEvent> results = new ArrayList<>();
            KubernetesStatus jobStatus = KubernetesStatus.FAILED;

            while (!Thread.currentThread().isInterrupted()) {
                final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(evaluationTask.taskId()).orElseThrow();

                try {
                    if (evaluationResultEntity.getPodIndicesCompleted().size() == settingsService.getReplicationFactor()) {
                        jobStatus = kubernetesService.waitForJobCompletion(evaluationTask.taskId(), settingsService.getReplicationFactor());
                    } else {
                        jobStatus = kubernetesService.getJobStatus(evaluationTask.taskId(), settingsService.getReplicationFactor());
                    }
                } catch (final ApiException e) {
                    final EvaluationEvent finalErrorResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(),
                            EvaluationStatus.FAILED, e.getMessage(), "", "");
                    results.add(finalErrorResult);
                    updateCallback.accept(finalErrorResult);
                    return new SingleEvaluationResult(evaluationTask,
                            EvaluationStatus.FAILED,
                            results);
                }


                if (jobStatus.isFailed()) {
                    throw new RuntimeException("Job failed.");
                }

                final EvaluationStatus evaluationStatus = EvaluationUtils.mapToEvaluationStatus(jobStatus);

                if (evaluationStatus.isFinal()) {
                    final EvaluationEvent evaluationEvent = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(),
                            evaluationStatus, "Evaluation finished.", "", "");
                    results.add(evaluationEvent);
                    updateCallback.accept(evaluationEvent);
                    break;
                }

                synchronized (this) {
                    try {
                        wait(10_000);
                    } catch (final InterruptedException e) {
                        throw new RuntimeException("Evaluation was interrupted.");
                    }
                }
            }

            final EvaluationStatus evaluationStatus = EvaluationUtils.mapToEvaluationStatus(jobStatus);
            return new SingleEvaluationResult(evaluationTask,
                    evaluationStatus,
                    results);
        });
    }

    public void notifyEvaluationFinished() {
        synchronized (this) {
            notifyAll();
        }
    }

}
