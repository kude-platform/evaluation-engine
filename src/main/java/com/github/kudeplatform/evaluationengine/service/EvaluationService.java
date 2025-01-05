package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.api.Event;
import com.github.kudeplatform.evaluationengine.api.IngestedEvent;
import com.github.kudeplatform.evaluationengine.async.EvaluationFinishedEvaluator;
import com.github.kudeplatform.evaluationengine.async.MultiEvaluator;
import com.github.kudeplatform.evaluationengine.domain.*;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapper;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import io.kubernetes.client.openapi.ApiException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.github.kudeplatform.evaluationengine.service.FileSystemService.KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR;

/**
 * @author timo.buechert
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationService {

    @Qualifier("evaluationTaskExecutor")
    final ThreadPoolTaskExecutor taskExecutor;

    @Qualifier(value = "asyncEvaluatorExecutorService")
    final ExecutorService asyncEvaluatorExecutorService;

    final EvaluationEventRepository evaluationEventRepository;

    final EvaluationResultRepository evaluationResultRepository;

    final MultiEvaluator multiEvaluator;

    final EvaluationEventMapper evaluationEventMapper;

    final KubernetesService kubernetesService;

    final SettingsService settingsService;

    final FileSystemService fileSystemService;

    final BlockingQueue<EvaluationTask> evaluationTaskQueue;

    final EvaluationFinishedEvaluator evaluationFinishedEvaluator;

    @Qualifier(value = "activeEvaluationViewComponents")
    final List<NotifiableComponent> activeEvaluationViewComponents;

    final Map<String, Future<Result>> evaluationFutures = new HashMap<>();

    final Map<String, EvaluationRunnable> evaluationIdsToRunnables = new HashMap<>();

    final List<Future<?>> activeEvaluationThreads = new ArrayList<>();

    private Semaphore evaluationLock;

    private boolean useWatchToDetectCompletionAsBoolean;

    @Getter
    private int numberOfNodes;

    @PostConstruct
    @Transactional
    public void init() throws ApiException {
        this.numberOfNodes = kubernetesService.getNumberOfNodes();
        final int maxNumberOfParallelJobs = calculateMaxNumberOfParallelJobs(this.settingsService.getReplicationFactor(),
                this.settingsService.getMaxJobsPerNode());

        this.evaluationLock = new Semaphore(maxNumberOfParallelJobs);
        this.cancelAllEvaluationTasks();
        this.deleteAllPreviousResults();

        for (int i = 0; i < maxNumberOfParallelJobs; i++) {
            final EvaluationRunnable evaluationRunnable = new EvaluationRunnable();
            activeEvaluationThreads.add(taskExecutor.submit(evaluationRunnable));
        }

        try {
            useWatchToDetectCompletionAsBoolean = Boolean.parseBoolean(settingsService.getUseWatchToDetectCompletion());
        } catch (final Exception e) {
            useWatchToDetectCompletionAsBoolean = false;
        }
    }

    @Transactional
    public synchronized void saveIngestedEvent(final IngestedEvent ingestedEvent) {
        for (final Event event : ingestedEvent.getEvents()) {
            handleEvent(ingestedEvent, event);
        }

        final boolean fatal = ingestedEvent.getEvents().stream().anyMatch(Event::isFatal);
        if (fatal) {
            failEvaluationTask(ingestedEvent.getEvaluationId(), false);
        }

        this.notifyView();
    }

    private synchronized void handleEvent(final IngestedEvent ingestedEvent, final Event event) {
        final List<EvaluationEventEntity> byTaskIdAndCategory =
                evaluationEventRepository.findByTaskIdAndType(ingestedEvent.getEvaluationId(), event.getType());

        if (!byTaskIdAndCategory.isEmpty()) {
            final EvaluationEventEntity evaluationEventEntity = byTaskIdAndCategory.get(0);
            evaluationEventEntity.setTimestamp(ZonedDateTime.now());
            evaluationEventEntity.setIndex(concatIndexIfNotYetContained(ingestedEvent, byTaskIdAndCategory));
            evaluationEventRepository.save(evaluationEventEntity);
        } else {
            final EvaluationEvent evaluationEvent = new EvaluationEvent(ingestedEvent.getEvaluationId(),
                    ZonedDateTime.now(),
                    EvaluationStatus.RUNNING,
                    event.getMessage(), ingestedEvent.getIndex(),
                    event.getType(), event.getLevel());

            evaluationEventRepository.save(evaluationEventMapper.toEntity(evaluationEvent));
        }
        final EvaluationResultEntity resultEntity = evaluationResultRepository.findById(ingestedEvent.getEvaluationId()).orElseThrow();
        resultEntity.setMessage(findLastMostImportantErrorEvent(ingestedEvent.getEvaluationId()));

        if (event.getType().equals("BUILD_COMPLETED")) {
            resultEntity.getPodIndicesReadyToRun().add(Integer.parseInt(ingestedEvent.getIndex()));
            evaluationResultRepository.save(resultEntity);
        }

        if (event.getType().equals("JOB_COMPLETED")) {
            resultEntity.getPodIndicesCompleted().add(Integer.parseInt(ingestedEvent.getIndex()));
            resultEntity.setNetEvaluationDurationInSeconds(event.getDurationInSeconds());
            evaluationResultRepository.save(resultEntity);

            if (!useWatchToDetectCompletionAsBoolean) {
                evaluationFinishedEvaluator.notifyEvaluationFinished();
            }
        }
        notifyView();
    }

    private String concatIndexIfNotYetContained(final IngestedEvent ingestedEvent,
                                                final List<EvaluationEventEntity> byTaskIdAndCategory) {
        if (Arrays.stream(byTaskIdAndCategory.get(0).getIndex().split(","))
                .anyMatch(index -> index.equals(ingestedEvent.getIndex()))) {
            return byTaskIdAndCategory.get(0).getIndex();
        }
        return byTaskIdAndCategory.get(0).getIndex() + "," + ingestedEvent.getIndex();
    }

    public boolean areAllPodsReadyToRun(final String taskId) {
        final EvaluationResultEntity resultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
        return resultEntity.getPodIndicesReadyToRun().size() == settingsService.getReplicationFactor();
    }

    public boolean isNoJobRunning() {
        return this.evaluationLock.availablePermits() == calculateMaxNumberOfParallelJobs(
                this.settingsService.getReplicationFactor(), this.settingsService.getMaxJobsPerNode());
    }

    public void updateNumberOfParallelJobs(final int newReplicationFactor, final int newMaxJobsPerNode) {
        if (isNoJobRunning()) {
            this.evaluationLock = new Semaphore(calculateMaxNumberOfParallelJobs(newReplicationFactor, newMaxJobsPerNode));
            this.activeEvaluationThreads.forEach(future -> future.cancel(true));
            this.activeEvaluationThreads.clear();

            for (int i = 0; i < calculateMaxNumberOfParallelJobs(newReplicationFactor, newMaxJobsPerNode); i++) {
                final EvaluationRunnable evaluationRunnable = new EvaluationRunnable();
                activeEvaluationThreads.add(taskExecutor.submit(evaluationRunnable));
            }
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
            evaluationResultEntity.setGitUrl(gitEvaluationTask.repositoryUrl());
            evaluationResultEntity.setGitBranch(gitEvaluationTask.gitBranch());

            final String gitUser = settingsService.getGitUsername();
            final String gitToken = settingsService.getGitToken();
            if (!gitUser.isEmpty() && !gitToken.isEmpty()) {
                final String gitUrl = gitEvaluationTask.repositoryUrl();
                gitEvaluationTask.setGitUrl(gitUrl.replace("https://", "https://" + gitUser + ":" + gitToken + "@"));
            }
        }

        evaluationResultRepository.save(evaluationResultEntity);
        evaluationTaskQueue.add(evaluationTask);
        if (notifyView) {
            notifyView();
        }
    }

    public synchronized void failEvaluationTask(final String taskId, final boolean notifyView) {
        evaluationTaskQueue.removeIf(task -> task.taskId().equals(taskId));

        if (evaluationIdsToRunnables.containsKey(taskId)) {
            evaluationIdsToRunnables.get(taskId).fail();
        }

        if (evaluationFutures.containsKey(taskId)) {
            evaluationFutures.get(taskId).cancel(true);
        }

        if (notifyView) {
            notifyView();
        }
    }

    public synchronized void cancelEvaluationTask(final String taskId, final boolean notifyView) {
        final boolean taskNotYetStarted = evaluationTaskQueue.stream().anyMatch(task -> task.taskId().equals(taskId));
        if (taskNotYetStarted) {
            evaluationTaskQueue.removeIf(task -> task.taskId().equals(taskId));
            final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
            evaluationResultEntity.setStatus(EvaluationStatus.CANCELLED);
            evaluationResultRepository.save(evaluationResultEntity);
        } else {
            if (evaluationIdsToRunnables.containsKey(taskId)) {
                evaluationIdsToRunnables.get(taskId).cancel();
            }

            if (evaluationFutures.containsKey(taskId)) {
                evaluationFutures.get(taskId).cancel(true);
            }
        }

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

    public void submitMassEvaluationTask(final String value, final List<String> instanceStartCommands, String branchName, String datasetName) {
        final String[] lines = value.split("\n");
        for (final String line : lines) {
            final String[] parts = line.split(";");
            if (parts.length == 2 && parts[0].trim().startsWith("http")) {
                final String repositoryUrl = parts[0].trim();
                final String name = parts[1].trim();
                final EvaluationTask evaluationTask =
                        new GitEvaluationTask(repositoryUrl, UUID.randomUUID().toString(), instanceStartCommands, name, branchName, datasetName);
                this.submitEvaluationTask(evaluationTask, false);
            }
        }
        notifyView();
    }

    @Transactional
    public void exportAllResultsToFile() {
        final List<EvaluationResultEntity> evaluationResultEntities = evaluationResultRepository.findAll();
        final List<EvaluationResultWithEvents> evaluationResultWithEventsList = new ArrayList<>();
        for (final EvaluationResultEntity evaluationResultEntity : evaluationResultEntities) {
            final List<EvaluationEventEntity> evaluationEventEntities = evaluationEventRepository.findByTaskId(evaluationResultEntity.getTaskId());

            int durationInSeconds = -1;
            if (evaluationResultEntity.getStartTimestamp() != null && evaluationResultEntity.getEndTimestamp() != null) {
                durationInSeconds = Math.toIntExact(Duration.between(evaluationResultEntity.getStartTimestamp(), evaluationResultEntity.getEndTimestamp()).getSeconds());

            }
            final EvaluationResultWithEvents evaluationResultWithEvents = EvaluationResultWithEvents.builder()
                    .taskId(evaluationResultEntity.getTaskId())
                    .name(evaluationResultEntity.getName())
                    .gitUrl(evaluationResultEntity.getGitUrl())
                    .gitBranch(evaluationResultEntity.getGitBranch())
                    .startTimestamp(evaluationResultEntity.getStartTimestamp())
                    .endTimestamp(evaluationResultEntity.getEndTimestamp())
                    .durationInSeconds(durationInSeconds)
                    .netDurationInSeconds(Integer.parseInt(Optional.ofNullable(evaluationResultEntity.getNetEvaluationDurationInSeconds()).orElse("0")))
                    .status(evaluationResultEntity.getStatus())
                    .logsAvailable(evaluationResultEntity.isLogsAvailable())
                    .resultsAvailable(evaluationResultEntity.isResultsAvailable())
                    .resultsCorrect(evaluationResultEntity.isResultsCorrect())
                    .resultProportion(evaluationResultEntity.getResultProportion())
                    .message(evaluationResultEntity.getMessage())
                    .events(evaluationEventEntities.stream().map(EvaluationEventEntity::getType).distinct().collect(Collectors.joining(",")))
                    .build();
            evaluationResultWithEventsList.add(evaluationResultWithEvents);
        }
        fileSystemService.saveToCsvFile(evaluationResultWithEventsList);
    }

    private int calculateMaxNumberOfParallelJobs(final int replicationFactor, final int maxJobsPerNode) {
        return (this.numberOfNodes * maxJobsPerNode) / replicationFactor;
    }

    public ResultsEvaluation areResultsCorrect(final String results) {
        final List<String> solutionList = Arrays.asList(settingsService.getExpectedSolution().split("\n"));
        final List<String> resultList = Arrays.asList(results.split("\n"));

        final List<String> cleanedSolutionList = solutionList.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        final List<String> cleanedResultList = resultList.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();

        int totalCorrect = 0;
        for (final String cleanedResult : cleanedResultList) {
            if (cleanedSolutionList.contains(cleanedResult)) {
                totalCorrect++;
            }
        }

        final boolean allCorrect = new HashSet<>(cleanedResultList).containsAll(cleanedSolutionList) && new HashSet<>(cleanedSolutionList).containsAll(cleanedResultList);
        final String resultProportion = String.format("%d/%d/%d", totalCorrect, cleanedResultList.size(), cleanedSolutionList.size());

        return new ResultsEvaluation(cleanedResultList.size(), totalCorrect, cleanedSolutionList.size(), allCorrect, resultProportion);
    }

    public String getTemplateStartCommand(final int instanceId, String datasetName) {
        if (instanceId == 0) {
            return String.format("java -Xms2048m -Xmx2048m -jar ./app.jar master -h $CURRENT_HOST -ia $POD_IP -kb true -ip /data/%s", datasetName);
        }

        return "java -Xms2048m -Xmx2048m -jar ./app.jar worker -mh $MASTER_HOST -h $CURRENT_HOST -ia $POD_IP -kb true -w 4";
    }

    class EvaluationRunnable implements Runnable {

        private boolean cancelled = false;

        private boolean interrupted = false;

        private boolean failed = false;

        public void cancel() {
            this.cancelled = true;
        }

        public void fail() {
            this.failed = true;
        }

        @Override
        public void run() {
            log.info("Evaluation thread with id {} started", Thread.currentThread().getId());
            try {
                while (!Thread.currentThread().isInterrupted() && !interrupted) {

                    EvaluationTask task = evaluationTaskQueue.take();
                    evaluationIdsToRunnables.put(task.taskId(), this);

                    try {
                        evaluationLock.acquire();

                        setStartTimestampNow(task);

                        updateTaskStatus(task, EvaluationStatus.DEPLOYING);
                        deploy(task);

                        kubernetesService.waitForJobRunning(task.taskId(), settingsService.getReplicationFactor());
                        updateTaskStatus(task, EvaluationStatus.RUNNING);

                        final ExecutorCompletionService<Result> completionService = new ExecutorCompletionService<>(asyncEvaluatorExecutorService);
                        final Future<Result> evaluationFuture = multiEvaluator.evaluate(task, this::evaluationEventCallback, completionService);
                        evaluationFutures.put(task.taskId(), evaluationFuture);

                        Result result;
                        try {
                            result = evaluationFuture.get(settingsService.getTimeoutInSeconds(), TimeUnit.SECONDS);
                        } catch (CancellationException exception) {
                            if (failed) {
                                log.info("Evaluation cancelled because of failed task {}", task.taskId());
                                result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                            } else {
                                log.info("Evaluation cancelled for task {}", task.taskId());
                                result = new SingleEvaluationResult(task, EvaluationStatus.CANCELLED, List.of());
                            }
                        } catch (TimeoutException exception) {
                            log.info("Evaluation timed out for task {}", task.taskId());
                            evaluationFuture.cancel(true);
                            result = new SingleEvaluationResult(task, EvaluationStatus.TIMEOUT, List.of());
                        } catch (ExecutionException e) {
                            log.error("Evaluation failed", e.getCause());
                            evaluationFuture.cancel(true);
                            result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                        }

                        setEndTimestampNow(task);

                        evaluationFutures.remove(task.taskId());
                        final EvaluationResultEntity resultEntity =
                                evaluationResultRepository.findById(task.taskId()).orElseThrow();
                        resultEntity.setStatus(result.getEvaluationStatus());
                        resultEntity.setMessage(findLastMostImportantErrorEvent(task.taskId()));
                        evaluationResultRepository.save(resultEntity);

                    } catch (final Exception e) {
                        log.error("Evaluation failed", e);
                        final EvaluationEvent evaluationEvent = new EvaluationEvent(task.taskId(),
                                ZonedDateTime.now(), EvaluationStatus.FAILED, e.getMessage(), "", "EVALUATION_FAILED", EvaluationEvent.LEVEL_FATAL);
                        this.evaluationEventCallback(evaluationEvent);
                        
                        final Result result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                        final EvaluationResultEntity resultEntity =
                                evaluationResultRepository.findById(task.taskId()).orElseThrow();
                        resultEntity.setStatus(result.getEvaluationStatus());
                        resultEntity.setMessage(findLastMostImportantErrorEvent(task.taskId()));
                        evaluationResultRepository.save(resultEntity);
                    } finally {
                        evaluationFutures.remove(task.taskId());
                        evaluationLock.release();
                        kubernetesService.deleteTask(task.taskId());
                        evaluationIdsToRunnables.remove(task.taskId());
                        notifyView();
                        this.cancelled = false;
                        this.failed = false;
                    }

                }
            } catch (final InterruptedException e) {
                log.info("Evaluation thread with id {} interrupted", Thread.currentThread().getId());
                interrupted = true;
            } catch (final Exception e) {
                log.error("Evaluation failed", e); // TODO: better error handling
            }

            log.info("Evaluation thread with id {} stopped", Thread.currentThread().getId());
        }

        private void updateTaskStatus(final EvaluationTask task, final EvaluationStatus evaluationStatus) {
            final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(task.taskId()).orElseThrow();
            evaluationResultEntity.setStatus(evaluationStatus);
            evaluationResultRepository.save(evaluationResultEntity);
            notifyView();
        }

        private void setStartTimestampNow(final EvaluationTask task) {
            final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(task.taskId()).orElseThrow();
            evaluationResultEntity.setStartTimestamp(ZonedDateTime.now());
            evaluationResultRepository.save(evaluationResultEntity);
        }

        private void setEndTimestampNow(final EvaluationTask task) {
            final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(task.taskId()).orElseThrow();
            evaluationResultEntity.setEndTimestamp(ZonedDateTime.now());
            evaluationResultRepository.save(evaluationResultEntity);
        }

        public void evaluationEventCallback(final EvaluationEvent result) {
            evaluationEventRepository.save(evaluationEventMapper.toEntity(result));
        }
    }

    private String findLastMostImportantErrorEvent(final String taskId) {
        final List<EvaluationEventEntity> events = evaluationEventRepository.findByTaskId(taskId);
        final Optional<EvaluationEventEntity> fatalEvent = events.stream().filter(e -> e.getLevel().equals(EvaluationEvent.LEVEL_FATAL)).reduce((first, second) -> second);
        if (fatalEvent.isPresent()) {
            return fatalEvent.get().getType();
        }

        final Optional<EvaluationEventEntity> errorEvent = events.stream().filter(e -> e.getLevel().equals(EvaluationEvent.LEVEL_ERROR)).reduce((first, second) -> second);
        if (errorEvent.isPresent()) {
            return errorEvent.get().getType();
        }

        return "";
    }

    private void deploy(EvaluationTask task) {
        if (task instanceof GitEvaluationTask gitEvaluationTask) {
            kubernetesService.deployTask(gitEvaluationTask, settingsService, settingsService.getMaxJobsPerNode() > 1);
        }
    }

    public void notifyView() {
        activeEvaluationViewComponents.forEach(NotifiableComponent::dataChanged);
    }


}
