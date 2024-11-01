package com.github.kudeplatform.evaluationengine.service;

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
import java.util.UUID;
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

    final Map<UUID, Future<Result>> evaluationFutures = new HashMap<>();


    @PostConstruct
    public void init() {
        this.cancelAllEvaluationTasks();
        taskExecutor.execute(new EvaluationRunnable());
    }

    public int getPositionInQueue(final UUID taskId) {
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

    public void cancelEvaluationTask(UUID taskId) {
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
                    evaluationLock.lock();
                    final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(task.taskId()).orElseThrow();
                    evaluationResultEntity.setStatus(EvaluationStatus.DEPLOYING);
                    evaluationResultEntity.setTimestamp(ZonedDateTime.now());
                    evaluationResultRepository.save(evaluationResultEntity);
                    notifyView();
                    deploy(task); //TODO: error handling

                    final CompletableFuture<Result> evaluationFuture = multiEvaluator.evaluate(task, this::evaluationEventCallback);
                    evaluationFutures.put(task.taskId(), evaluationFuture);

                    Result result;
                    try {
                        result = evaluationFuture.get(settingsService.getTimeoutInSeconds(), TimeUnit.SECONDS);
                    } catch (CancellationException exception) {
                        result = new SingleEvaluationResult(task, EvaluationStatus.CANCELLED, List.of());
                    } catch (TimeoutException exception) {
                        result = new SingleEvaluationResult(task, EvaluationStatus.TIMEOUT, List.of());
                    } catch (ExecutionException e) {
                        result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                    }
                    boolean logsAvailable = true;
                    logsAvailable = false; //skip logs for now
//                    try {
//                        HashMap<String, List<KubernetesService.LogFile>> logs = kubernetesService.getLogs(task.taskId().toString());
//                        final String fileName = "logs-" + task.taskId().toString();
//                        final File zipFile = new File(System.getProperty("java.io.tmpdir") + fileName + ".zip");
//
//                        try (FileOutputStream fileOut = new FileOutputStream(zipFile);
//                             ZipOutputStream zipFileOut = new ZipOutputStream(fileOut)) {
//
//                            for (String podname : logs.keySet()) {
//                                final List<KubernetesService.LogFile> logFilesOfPod = logs.get(podname);
//                                for (final KubernetesService.LogFile logFile : logFilesOfPod) {
//                                    zipFileOut.putNextEntry(new ZipEntry(podname + logFile.name()));
//                                    IOUtils.copy(new ByteArrayInputStream(logFile.content()), zipFileOut);
//                                    zipFileOut.closeEntry();
//                                }
//                            }
//                            zipFileOut.finish();
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
//
//                    } catch (ApiException | OrchestrationServiceException | RuntimeException e) {
//                        log.error("Failed to get logs for task {}", task.taskId(), e);
//                        logsAvailable = false; //TODO: handle exception
//                    }

                    //kubernetesService.deleteTask(task.taskId().toString());

                    evaluationFutures.remove(task.taskId());
                    final EvaluationResultEntity resultEntity =
                            evaluationResultRepository.findById(task.taskId()).orElseThrow();
                    resultEntity.setStatus(result.getEvaluationStatus());
                    resultEntity.setLogsAvailable(logsAvailable);
                    evaluationResultRepository.save(resultEntity);
                    notifyView();

                    evaluationLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Evaluation failed", e); // TODO: better error handling
            } finally {
                evaluationLock.unlock();
            }
        }

        public void evaluationEventCallback(final EvaluationEvent result) {
            evaluationEventRepository.save(evaluationEventMapper.toEntity(result));
        }
    }

    private void deploy(EvaluationTask task) {
        if (task instanceof FileEvaluationTask) {
            kubernetesService.deployTask(task.taskId().toString(), task.additionalCommandLineOptions(),
                    settingsService.getReplicationFactor(), settingsService.getTimeoutInSeconds());
        } else if (task instanceof GitEvaluationTask gitEvaluationTask) {
            kubernetesService.deployTask(task.taskId().toString(), gitEvaluationTask.repositoryUrl(),
                    task.additionalCommandLineOptions(), settingsService.getReplicationFactor(),
                    settingsService.getTimeoutInSeconds());
        }
    }

    private void notifyView() {
        activeViewComponents.forEach(NotifiableComponent::dataChanged);
    }


}
