package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.*;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.KubernetesService;
import com.github.kudeplatform.evaluationengine.service.KubernetesStatus;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
    Gson gson;

    @Autowired
    SettingsService settingsService;

    @Autowired
    EvaluationResultRepository evaluationResultRepository;

    @Value("${USE_WATCH_TO_DETECT_COMPLETION:false}")
    private String useWatchToDetectCompletion;

    private boolean useWatchToDetectCompletionAsBoolean;

    @PostConstruct
    public void init() {
        try {
            useWatchToDetectCompletionAsBoolean = Boolean.parseBoolean(useWatchToDetectCompletion);
        } catch (final Exception e) {
            useWatchToDetectCompletionAsBoolean = false;
        }
    }

    @Override
    public Future<Result> evaluate(final EvaluationTask evaluationTask,
                                   final Consumer<EvaluationEvent> updateCallback,
                                   final CompletionService<Result> completionService) {
        return completionService.submit(() -> {
            if (useWatchToDetectCompletionAsBoolean) {
                return evaluateWithWatch(evaluationTask, updateCallback);
            } else {
                return evaluateWithPolling(evaluationTask, updateCallback);
            }
        });
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
            final EvaluationEvent finalErrorResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(), EvaluationStatus.FAILED, e.getMessage(), "", "");
            results.add(finalErrorResult);
            updateCallback.accept(finalErrorResult);
            return new SingleEvaluationResult(evaluationTask,
                    EvaluationStatus.FAILED,
                    results);
        }

        final EvaluationStatus evaluationStatus = EvaluationUtils.mapToEvaluationStatus(jobStatus);

        if (EvaluationStatus.FAILED.equals(evaluationStatus)) {
            return new SingleEvaluationResult(evaluationTask, evaluationStatus, results);
        }

        final EvaluationEvent finalResult = new EvaluationEvent(evaluationTask.taskId(), ZonedDateTime.now(),
                evaluationStatus, "Evaluation finished.", "", "");
        results.add(finalResult);
        updateCallback.accept(finalResult);
        return new SingleEvaluationResult(evaluationTask,
                evaluationStatus,
                results);
    }

    private Result evaluateWithPolling(final EvaluationTask evaluationTask, final Consumer<EvaluationEvent> updateCallback) {
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


            final EvaluationStatus evaluationStatus = EvaluationUtils.mapToEvaluationStatus(jobStatus);

            if (EvaluationStatus.FAILED.equals(evaluationStatus)) {
                return new SingleEvaluationResult(evaluationTask, evaluationStatus, results);
            }

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
    }

    public void notifyEvaluationFinished() {
        synchronized (this) {
            notifyAll();
        }
    }

}
