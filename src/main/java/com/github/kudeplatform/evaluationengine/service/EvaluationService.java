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
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import io.kubernetes.client.openapi.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.kudeplatform.evaluationengine.service.FileSystemService.KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR;

/**
 * @author timo.buechert
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    static final int NUMBER_OF_THREADS = 4;

    final ThreadPoolTaskExecutor taskExecutor;

    final EvaluationEventRepository evaluationEventRepository;

    final EvaluationResultRepository evaluationResultRepository;

    final MultiEvaluator multiEvaluator;

    final EvaluationEventMapper evaluationEventMapper;

    final KubernetesService kubernetesService;

    final SettingsService settingsService;

    final BlockingQueue<EvaluationTask> evaluationTaskQueue;

    final List<NotifiableComponent> activeViewComponents;

    final Map<String, Future<Result>> evaluationFutures = new HashMap<>();

    final Map<String, EvaluationRunnable> evaluationIdsToRunnables = new HashMap<>();

    final List<EvaluationRunnable> evaluationRunnables = new ArrayList<>();

    Semaphore evaluationLock;

    int numberOfNodes;

    @PostConstruct
    public void init() throws ApiException {
        this.numberOfNodes = kubernetesService.getNumberOfNodes();
        this.evaluationLock = new Semaphore(calculateMaxNumberOfParallelJobs(this.settingsService.getReplicationFactor()));
        this.cancelAllEvaluationTasks();
        this.deleteAllPreviousResults();

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            final EvaluationRunnable evaluationRunnable = new EvaluationRunnable();
            evaluationRunnables.add(evaluationRunnable);
            taskExecutor.execute(evaluationRunnable);
        }
    }

    @Transactional
    public void saveIngestedEvent(final IngestedEvent ingestedEvent) {
        for (final String error : ingestedEvent.getErrors()) {
            final List<EvaluationEventEntity> byTaskIdAndCategory =
                    evaluationEventRepository.findByTaskIdAndCategory(ingestedEvent.getEvaluationId(), error);

            if (!byTaskIdAndCategory.isEmpty()) {
                final EvaluationEventEntity evaluationEventEntity = byTaskIdAndCategory.get(0);
                evaluationEventEntity.setTimestamp(ZonedDateTime.now());
                evaluationEventEntity.setIndex(byTaskIdAndCategory.get(0).getIndex() + "," + ingestedEvent.getIndex());
                evaluationEventRepository.save(evaluationEventEntity);
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

    public boolean isNoJobRunning() {
        return this.evaluationLock.availablePermits() == calculateMaxNumberOfParallelJobs(this.settingsService.getReplicationFactor());
    }

    public void updateNumberOfParallelJobs(final int newReplicationFactor) {
        if (isNoJobRunning()) {
            this.evaluationLock = new Semaphore(calculateMaxNumberOfParallelJobs(newReplicationFactor));
        } else {
            log.error("Cannot update number of parallel jobs while jobs are running");
            throw new IllegalStateException("Cannot update number of parallel jobs while jobs are running");
        }
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

    public void submitEvaluationTask(final EvaluationTask evaluationTask, final boolean notifyView) {
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId(evaluationTask.taskId());
        evaluationResultEntity.setStatus(EvaluationStatus.PENDING);
        evaluationResultEntity.setName(evaluationTask.name());

        if (evaluationTask instanceof GitEvaluationTask gitEvaluationTask) {
            String gitUser = settingsService.getGitUsername();
            String gitToken = settingsService.getGitToken();
            if (!gitUser.isEmpty() && !gitToken.isEmpty()) {
                final String gitUrl = gitEvaluationTask.repositoryUrl();
                gitEvaluationTask.setRepositoryUrl(gitUrl.replace("https://", "https://" + gitUser + ":" + gitToken + "@"));
            }
        }

        evaluationResultRepository.save(evaluationResultEntity);
        evaluationTaskQueue.add(evaluationTask);
        if (notifyView) {
            notifyView();
        }
    }

    public void cancelEvaluationTask(String taskId, boolean notifyView) {
        evaluationTaskQueue.removeIf(task -> task.taskId().equals(taskId));

        if (evaluationFutures.containsKey(taskId)) {
            evaluationFutures.get(taskId).cancel(true);
        }

        if (evaluationIdsToRunnables.containsKey(taskId)) {
            evaluationIdsToRunnables.get(taskId).cancel();
        }

        final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
        evaluationResultEntity.setStatus(EvaluationStatus.CANCELLED);
        evaluationResultRepository.save(evaluationResultEntity);

        if (notifyView) {
            notifyView();
        }
    }

    @Transactional
    public void cancelAllEvaluationTasks() {
        evaluationResultRepository.findAll()
                .stream()
                .filter(evaluationResultEntity -> !evaluationResultEntity.getStatus().isFinal())
                .forEach(evaluationResultEntity -> this.cancelEvaluationTask(evaluationResultEntity.getTaskId(), false));
        notifyView();
    }

    @Transactional
    public void deleteEvaluationTask(String taskId) {
        deleteFilesInTmpDirByPattern(taskId);

        evaluationEventRepository.deleteByTaskId(taskId);
        evaluationResultRepository.deleteById(taskId);
        notifyView();
    }


    @Transactional
    public void deleteAllPreviousResults() {
        deleteFilesInTmpDirByPattern("");
    }

    @Transactional
    public void deleteAllEvaluationTasks() {
        cancelAllEvaluationTasks();
        deleteAllPreviousResults();
        evaluationEventRepository.deleteAll();
        evaluationResultRepository.deleteAll();
        notifyView();
    }

    private void deleteFilesInTmpDirByPattern(final String pattern) {
        final File folder = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR);
        final File[] files = folder.listFiles((dir, name) -> name.contains(pattern));
        Optional.ofNullable(files).ifPresent(f -> {
            for (final File file : f) {
                if (!file.delete()) {
                    log.error("Could not delete file {}", file.getName());
                }
            }
        });
    }

    public void submitMassEvaluationTask(final String value, final String additionalCommandLineOptions) {
        final String[] lines = value.split("\n");
        for (final String line : lines) {
            final String[] parts = line.split(";");
            if (parts.length == 2 && parts[0].trim().startsWith("http")) {
                final String repositoryUrl = parts[0].trim();
                final String name = parts[1].trim();
                final EvaluationTask evaluationTask =
                        new GitEvaluationTask(repositoryUrl, UUID.randomUUID().toString(), additionalCommandLineOptions, name);
                this.submitEvaluationTask(evaluationTask, false);
            }
        }
        notifyView();
    }

    private int calculateMaxNumberOfParallelJobs(final int replicationFactor) {
        return this.numberOfNodes / replicationFactor;
    }

    class EvaluationRunnable implements Runnable {

        private boolean cancelled = false;

        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public void run() {
            try {
                while (true) {

                    EvaluationTask task = evaluationTaskQueue.take();
                    evaluationIdsToRunnables.put(task.taskId(), this);

                    try {
                        evaluationLock.acquire();

                        if (cancelled) {
                            final EvaluationResultEntity resultEntity =
                                    evaluationResultRepository.findById(task.taskId()).orElseThrow();
                            resultEntity.setStatus(EvaluationStatus.CANCELLED);
                            evaluationResultRepository.save(resultEntity);
                            continue;
                        }
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
                        log.error("Evaluation failed", e);
                        final EvaluationEvent evaluationEvent = new EvaluationEvent(task.taskId(),
                                ZonedDateTime.now(), EvaluationStatus.FAILED, e.getMessage(), "", "Evaluation failed");
                        this.evaluationEventCallback(evaluationEvent);
                        
                        final Result result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                        final EvaluationResultEntity resultEntity =
                                evaluationResultRepository.findById(task.taskId()).orElseThrow();
                        resultEntity.setStatus(result.getEvaluationStatus());
                        evaluationResultRepository.save(resultEntity);
                    } finally {
                        evaluationFutures.remove(task.taskId());
                        evaluationLock.release();
                        kubernetesService.deleteTask(task.taskId());
                        evaluationIdsToRunnables.remove(task.taskId());
                        notifyView();
                        this.cancelled = false;
                    }

                }
            } catch (Exception e) {
                log.error("Evaluation failed", e); // TODO: better error handling
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

    public void notifyView() {
        activeViewComponents.forEach(NotifiableComponent::dataChanged);
    }


}
