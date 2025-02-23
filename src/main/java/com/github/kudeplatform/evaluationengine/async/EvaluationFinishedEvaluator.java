package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.*;
import com.github.kudeplatform.evaluationengine.service.KubernetesService;
import com.github.kudeplatform.evaluationengine.service.KubernetesStatus;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InterruptedIOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author timo.buechert
 */
@Component
@Qualifier("parallelEvaluator")
@Slf4j
public class EvaluationFinishedEvaluator extends SimpleEvaluator {

    @Autowired
    KubernetesService kubernetesService;

    @Autowired
    SettingsService settingsService;

    @Override
    public Future<Result> evaluate(final EvaluationTask evaluationTask,
                                   final Consumer<EvaluationEvent> updateCallback,
                                   final CompletionService<Result> completionService) {
        return completionService.submit(() -> evaluateWithWatch(evaluationTask, updateCallback));
    }

    private Result evaluateWithWatch(final EvaluationTask evaluationTask, final Consumer<EvaluationEvent> updateCallback) {
        final List<EvaluationEvent> results = new ArrayList<>();
        KubernetesStatus jobStatus;
        try {
            jobStatus = kubernetesService.waitForJobCompletion(evaluationTask.taskId(), settingsService.getReplicationFactor());
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause() instanceof InterruptedIOException) {
                log.debug("Evaluation was cancelled.");
                return new SingleEvaluationResult(evaluationTask, EvaluationStatus.CANCELLED, new ArrayList<>());
            }

            log.error("Error while evaluating task: {}", evaluationTask.taskId(), e);
            final EvaluationEvent finalErrorResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(), EvaluationStatus.FAILED, e.getMessage(), "", "", EvaluationEvent.LEVEL_ERROR);
            results.add(finalErrorResult);
            updateCallback.accept(finalErrorResult);
            return new SingleEvaluationResult(evaluationTask,
                    EvaluationStatus.FAILED,
                    results);
        }

        final EvaluationStatus evaluationStatus = EvaluationUtils.mapToEvaluationStatus(jobStatus);

        return new SingleEvaluationResult(evaluationTask, evaluationStatus, results);
    }

    public void notifyEvaluationFinished() {
        synchronized (this) {
            notifyAll();
        }
    }

}
