package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.api.IngestedEvent;
import com.github.kudeplatform.evaluationengine.async.MultiEvaluator;
import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.FileEvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.GitEvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.domain.SingleEvaluationResult;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapper;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationResultMapper;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author timo.buechert
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    final ThreadPoolTaskExecutor taskExecutor;

    final EvaluationEventRepository evaluationEventRepository;

    final EvaluationResultRepository evaluationResultRepository;

    final MultiEvaluator multiEvaluator;

    final EvaluationEventMapper evaluationEventMapper;

    final EvaluationResultMapper evaluationResultMapper;

    final KubernetesService kubernetesService;

    final SettingsService settingsService;

    final BlockingQueue<EvaluationTask> evaluationTaskQueue;

    final List<NotifiableComponent> activeViewComponents;

    final ReentrantLock evaluationLock = new ReentrantLock();

    final Map<String, Future<Result>> evaluationFutures = new HashMap<String, Future<Result>>();


    @PostConstruct
    public void init() {
        this.cancelAllEvaluationTasks();
        taskExecutor.execute(new EvaluationRunnable());
    }

    public void saveIngestedEvent(final IngestedEvent ingestedEvent) {
        for (final String error : ingestedEvent.getErrors()) {
            final List<EvaluationEventEntity> byTaskIdAndCategoryAndIndex =
                    evaluationEventRepository.findByTaskIdAndCategoryAndIndex(ingestedEvent.getEvaluationId(), ingestedEvent.getIndex(), error);

            if (!byTaskIdAndCategoryAndIndex.isEmpty()) {
                byTaskIdAndCategoryAndIndex.get(0).setTimestamp(ZonedDateTime.now());
            } else {
                final EvaluationEvent evaluationEvent = new EvaluationEvent(ingestedEvent.getEvaluationId(),
                        ZonedDateTime.now(),
                        EvaluationStatus.RUNNING,
                        "", ingestedEvent.getIndex(),
                        error);

                evaluationEventRepository.save(evaluationEventMapper.toEntity(evaluationEvent));
            }
        }
        this.notifyView();
    }

    public int getPositionInQueue(final String taskId) {
        int positionInQueue = 1;
        for (final EvaluationTask taskInQueue : evaluationTaskQueue) {
            if (taskInQueue.taskId().equals(taskId)) {
                return positionInQueue;
            }
            positionInQueue++;
        }

        return -1;
    }

    public void submitEvaluationTask(final EvaluationTask evaluationTask) {
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId(evaluationTask.taskId());
        evaluationResultEntity.setStatus(EvaluationStatus.PENDING);

        evaluationResultRepository.save(evaluationResultEntity);
        evaluationTaskQueue.add(evaluationTask);
        notifyView();
    }

    public void cancelEvaluationTask(String taskId) {
        evaluationTaskQueue.removeIf(task -> task.taskId().equals(taskId));

        if (evaluationFutures.containsKey(taskId)) {
            evaluationFutures.get(taskId).cancel(true);
        } else {
            final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
            evaluationResultEntity.setStatus(EvaluationStatus.CANCELLED);
            evaluationResultRepository.save(evaluationResultEntity);
            notifyView();
        }
    }

    public void cancelAllEvaluationTasks() {
        this.kubernetesService.deleteAllTasks();
    }

    class EvaluationRunnable implements Runnable {

        @Override
        public void run() {
            try {
                while (true) {
                    final EvaluationTask task = evaluationTaskQueue.take();

                    try {
                        evaluationLock.lock();
                        updateTaskStatus(task, EvaluationStatus.DEPLOYING);
                        deploy(task);

                        kubernetesService.waitForJobRunning(task.taskId(), settingsService.getReplicationFactor());
                        updateTaskStatus(task, EvaluationStatus.RUNNING);

                        final CompletableFuture<Result> evaluationFuture = multiEvaluator.evaluate(task, this::evaluationEventCallback);
                        evaluationFutures.put(task.taskId(), evaluationFuture);

                        Result result;
                        try {
                            result = evaluationFuture.get(settingsService.getTimeoutInSeconds(), TimeUnit.SECONDS);
                        } catch (CancellationException exception) {
                            log.info("Evaluation cancelled for task {}", task.taskId());
                            result = new SingleEvaluationResult(task, EvaluationStatus.CANCELLED, List.of());
                        } catch (TimeoutException exception) {
                            log.info("Evaluation timed out for task {}", task.taskId());
                            result = new SingleEvaluationResult(task, EvaluationStatus.TIMEOUT, List.of());
                        } catch (ExecutionException e) {
                            log.error("Evaluation failed", e.getCause());
                            result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                        }

                        evaluationFutures.remove(task.taskId());
                        final EvaluationResultEntity resultEntity =
                                evaluationResultRepository.findById(task.taskId()).orElseThrow();
                        resultEntity.setStatus(result.getEvaluationStatus());
                        evaluationResultRepository.save(resultEntity);

                    } catch (Exception e) {
                        log.error("Evaluation failed", e); // TODO: better error handling

                        Result result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                        evaluationFutures.remove(task.taskId());
                        final EvaluationResultEntity resultEntity =
                                evaluationResultRepository.findById(task.taskId()).orElseThrow();
                        resultEntity.setStatus(result.getEvaluationStatus());
                        evaluationResultRepository.save(resultEntity);
                    } finally {
                        evaluationLock.unlock();
                        kubernetesService.deleteTask(task.taskId());
                        notifyView();
                    }

                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Evaluation failed", e); // TODO: better error handling
            } finally {
                evaluationLock.unlock();
            }
        }

        private void updateTaskStatus(final EvaluationTask task, final EvaluationStatus evaluationStatus) {
            final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(task.taskId()).orElseThrow();
            evaluationResultEntity.setStatus(evaluationStatus);
            evaluationResultEntity.setTimestamp(ZonedDateTime.now());
            evaluationResultRepository.save(evaluationResultEntity);
            notifyView();
        }

        public void evaluationEventCallback(final EvaluationEvent result) {
            evaluationEventRepository.save(evaluationEventMapper.toEntity(result));
        }
    }

    private void deploy(EvaluationTask task) {
        if (task instanceof FileEvaluationTask) {
            kubernetesService.deployTask(task.taskId(), task.additionalCommandLineOptions(),
                    settingsService.getReplicationFactor(), settingsService.getTimeoutInSeconds());
        } else if (task instanceof GitEvaluationTask gitEvaluationTask) {
            kubernetesService.deployTask(task.taskId(), gitEvaluationTask.repositoryUrl(),
                    task.additionalCommandLineOptions(), settingsService.getReplicationFactor(),
                    settingsService.getTimeoutInSeconds());
        }
    }

    private void notifyView() {
        activeViewComponents.forEach(NotifiableComponent::dataChanged);
    }


}
