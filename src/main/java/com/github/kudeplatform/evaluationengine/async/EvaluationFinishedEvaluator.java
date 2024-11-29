package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.domain.SingleEvaluationResult;
import com.github.kudeplatform.evaluationengine.service.KubernetesService;
import com.github.kudeplatform.evaluationengine.service.KubernetesStatus;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.google.gson.Gson;
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

    @Override
    public CompletableFuture<Result> evaluate(final EvaluationTask evaluationTask,
                                              final Consumer<EvaluationEvent> updateCallback) {
        return CompletableFuture.supplyAsync(() -> {
            final List<EvaluationEvent> results = new ArrayList<>();
            KubernetesStatus jobStatus;
            try {
                jobStatus = kubernetesService.waitForJobCompletion(evaluationTask.taskId(), settingsService.getReplicationFactor());
            } catch (Exception e) {
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

            final EvaluationEvent finalResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(),
                    evaluationStatus, "Evaluation finished.", "", "");
            results.add(finalResult);
            updateCallback.accept(finalResult);
            return new SingleEvaluationResult(evaluationTask,
                    evaluationStatus,
                    results);
        });
    }

}
