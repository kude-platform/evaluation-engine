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
import com.github.kudeplatform.evaluationengine.util.TextUtil;
import com.github.kudeplatform.evaluationengine.view.NotifiableComponent;
import io.kubernetes.client.openapi.ApiException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
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
public class EvaluationService implements ApplicationContextAware {

    @Qualifier("evaluationTaskExecutor")
    final ThreadPoolTaskExecutor taskExecutor;

    @Qualifier(value = "asyncEvaluatorExecutorService")
    final ExecutorService asyncEvaluatorExecutorService;

    final EvaluationEventRepository evaluationEventRepository;

    final EvaluationResultRepository evaluationResultRepository;

    final MultiEvaluator multiEvaluator;

    final EvaluationEventMapper evaluationEventMapper;

    final KubernetesService kubernetesService;

    final DataManagerService dataManagerService;

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

    @Getter
    private int numberOfNodes;

    private final HashMap<String, List<Integer>> podIndicesReadyToRun = new HashMap<>();

    private ApplicationContext context;

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    public EvaluationService getSelfReference() {
        return this.context.getBean(EvaluationService.class);
    }

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

    }

    public synchronized void saveIngestedEventAndNotifyView(final IngestedEvent ingestedEvent) {
        getSelfReference().saveIngestedEvent(ingestedEvent);
        this.notifyView(ingestedEvent.getEvaluationId());
    }

    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    // Some kind of thread synchronization is required here to prevent race conditions when multiple threads submit events concurrently
    @Transactional
    public synchronized void saveIngestedEvent(final IngestedEvent ingestedEvent) {
        for (final Event event : ingestedEvent.getEvents()) {
            handleEvent(ingestedEvent, event);
        }

        final boolean fatal = ingestedEvent.getEvents().stream().anyMatch(Event::isFatal);
        if (fatal) {
            failEvaluationTask(ingestedEvent.getEvaluationId(), false);
        }
    }

    private synchronized void handleEvent(final IngestedEvent ingestedEvent, final Event event) {
        final List<EvaluationEventEntity> byTaskIdAndCategory =
                evaluationEventRepository.findByTaskIdAndType(ingestedEvent.getEvaluationId(), event.getType());

        if (!byTaskIdAndCategory.isEmpty()) {
            final EvaluationEventEntity evaluationEventEntity = byTaskIdAndCategory.get(0);
            evaluationEventEntity.setTimestamp(ZonedDateTime.now());

            // TODO: maybe rework database design to avoid concatenation of indices or move to the db level
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
        resultEntity.setMessage(getSelfReference().findLastMostImportantErrorEvent(ingestedEvent.getEvaluationId()));

        if (event.getType().equals("BUILD_COMPLETED")) {
            synchronized (podIndicesReadyToRun) {
                podIndicesReadyToRun.get(ingestedEvent.getEvaluationId()).add(Integer.parseInt(ingestedEvent.getIndex()));
            }

        }

        if (event.getType().equals("JOB_COMPLETED")) {
            resultEntity.getPodIndicesCompleted().add(Integer.parseInt(ingestedEvent.getIndex()));
            if (Objects.equals(ingestedEvent.getIndex(), "0")) {
                resultEntity.setNetEvaluationDurationInSeconds(event.getDurationInSeconds());
            }
        }
        evaluationResultRepository.save(resultEntity);
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
        return podIndicesReadyToRun.containsKey(taskId) && podIndicesReadyToRun.get(taskId).size() == settingsService.getReplicationFactor();
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
        getSelfReference().saveEvaluationEntity(evaluationTask);

        if (evaluationTask instanceof GitEvaluationTask gitEvaluationTask) {
            final String gitUser = settingsService.getGitUsername();
            final String gitToken = settingsService.getGitToken();
            if (!gitUser.isEmpty() && !gitToken.isEmpty()) {
                final String gitUrl = gitEvaluationTask.repositoryUrl();
                gitEvaluationTask.setGitUrl(gitUrl.replace("https://", "https://" + gitUser + ":" + gitToken + "@"));
            }
        }

        evaluationTaskQueue.add(evaluationTask);
        if (notifyView) {
            notifyView(evaluationTask.taskId());
        }
    }

    @Transactional
    public void saveEvaluationEntity(final EvaluationTask evaluationTask) {
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId(evaluationTask.taskId());
        evaluationResultEntity.setStatus(EvaluationStatus.PENDING);
        evaluationResultEntity.setName(evaluationTask.name());
        evaluationResultEntity.setDatasetName(evaluationTask.datasetName());
        evaluationResultEntity.setMasterStartCommand(evaluationTask.instanceStartCommands().get(0));
        evaluationResultEntity.setFirstWorkerStartCommand(evaluationTask.instanceStartCommands().size() > 1 ? evaluationTask.instanceStartCommands().get(1) : "");

        if (evaluationTask instanceof GitEvaluationTask gitEvaluationTask) {
            evaluationResultEntity.setGitUrl(gitEvaluationTask.repositoryUrl());
            evaluationResultEntity.setGitBranch(gitEvaluationTask.gitBranch());
        }
        evaluationResultRepository.save(evaluationResultEntity);
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
            notifyView(taskId);
        }
    }

    public synchronized void cancelEvaluationTaskAndNotifyView(final String taskId, final boolean notifyView) {
        final boolean taskNotYetStarted = evaluationTaskQueue.stream().anyMatch(task -> task.taskId().equals(taskId));
        if (taskNotYetStarted) {
            evaluationTaskQueue.removeIf(task -> task.taskId().equals(taskId));
            getSelfReference().updateTaskStatus(taskId, EvaluationStatus.CANCELLED);
        } else {
            if (evaluationFutures.containsKey(taskId)) {
                evaluationFutures.get(taskId).cancel(true);
            }
        }

        if (notifyView) {
            notifyView(taskId);
        }
    }

    public void cancelAllEvaluationTasks() {
        evaluationResultRepository.findAll()
                .stream()
                .filter(evaluationResultEntity -> !evaluationResultEntity.getStatus().isFinal())
                .forEach(evaluationResultEntity -> this.cancelEvaluationTaskAndNotifyView(evaluationResultEntity.getTaskId(), false));
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
        final List<Repository> repositories = TextUtil.parseRepositoriesFromMassInput(value, settingsService.getGitUsername(), settingsService.getGitToken());

        for (final Repository repository : repositories) {
            final GitEvaluationTask gitEvaluationTask = new GitEvaluationTask(repository.url(), UUID.randomUUID().toString(),
                    instanceStartCommands, repository.name(), branchName, datasetName);
            getSelfReference().submitEvaluationTask(gitEvaluationTask, false);
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
                    .datasetName(evaluationResultEntity.getDatasetName())
                    .masterStartCommand(evaluationResultEntity.getMasterStartCommand())
                    .firstWorkerStartCommand(evaluationResultEntity.getFirstWorkerStartCommand())
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

    @Transactional
    public List<EvaluationResultEntity> getFinishedEvaluationEntities() {
        return evaluationResultRepository.findAll().stream()
                .filter(evaluationResultEntity -> evaluationResultEntity.getStatus().isFinal())
                .collect(Collectors.toList());
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
        return switch (settingsService.getMode()) {
            case AKKA -> getAkkaTemplateStartCommand(instanceId, datasetName);
            case SPARK -> getSparkTemplateStartCommand(instanceId, datasetName);
        };
    }

    private String getAkkaTemplateStartCommand(int instanceId, String datasetName) {
        if (instanceId == 0) {
            return String.format("java -Xms2048m -Xmx2048m -jar ./app.jar master -h $CURRENT_HOST -ip /data/%s -w 4", datasetName);
        }

        return "java -Xms2048m -Xmx2048m -jar ./app.jar worker -mh $MASTER_HOST -h $CURRENT_HOST -w 4";
    }

    private String getSparkTemplateStartCommand(int instanceId, String datasetName) {
        if (instanceId == 0) {
            return String.format("spark-submit --master spark://localhost:7077 app.jar --path /data/%s --master spark://$MASTER_HOST", datasetName);
        }

        return "EMPTY";
    }

    class EvaluationRunnable implements Runnable {

        private boolean interrupted = false;

        private boolean failed = false;

        public void fail() {
            this.failed = true;
        }

        @Override
        public void run() {
            log.info("Evaluation thread with id {} started", Thread.currentThread().threadId());
            try {
                while (!Thread.currentThread().isInterrupted() && !interrupted) {

                    EvaluationTask task = evaluationTaskQueue.take();
                    evaluationIdsToRunnables.put(task.taskId(), this);
                    podIndicesReadyToRun.put(task.taskId(), new ArrayList<>());

                    try {
                        if (isNoJobRunning()) {
                            dataManagerService.disableDataManagerSynchronization();
                            log.info("Disabled data manager synchronization");
                        }

                        evaluationLock.acquire();

                        getSelfReference().setStartTimestampNow(task.taskId());

                        getSelfReference().updateTaskStatus(task.taskId(), EvaluationStatus.DEPLOYING);
                        notifyView(task.taskId());

                        deploy(task);

                        kubernetesService.waitForJobRunning(task.taskId(), settingsService.getReplicationFactor());
                        getSelfReference().updateTaskStatus(task.taskId(), EvaluationStatus.RUNNING);
                        notifyView(task.taskId());

                        final ExecutorCompletionService<Result> completionService = new ExecutorCompletionService<>(asyncEvaluatorExecutorService);
                        final Future<Result> evaluationFuture = multiEvaluator.evaluate(task, getSelfReference()::evaluationEventCallback, completionService);
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

                        getSelfReference().setEndTimestampNow(task.taskId());

                        evaluationFutures.remove(task.taskId());
                        getSelfReference().updateTaskStatusAndMessage(task.taskId(), result.getEvaluationStatus(),
                                getSelfReference().findLastMostImportantErrorEvent(task.taskId()));

                    } catch (final Exception e) {
                        log.error("Evaluation failed", e);
                        final EvaluationEvent evaluationEvent = new EvaluationEvent(task.taskId(),
                                ZonedDateTime.now(), EvaluationStatus.FAILED, e.getMessage(), "", "EVALUATION_FAILED", EvaluationEvent.LEVEL_FATAL);
                        getSelfReference().evaluationEventCallback(evaluationEvent);
                        
                        final Result result = new SingleEvaluationResult(task, EvaluationStatus.FAILED, List.of());
                        getSelfReference().updateTaskStatusAndMessage(task.taskId(), result.getEvaluationStatus(),
                                getSelfReference().findLastMostImportantErrorEvent(task.taskId()));
                    } finally {
                        evaluationFutures.remove(task.taskId());
                        evaluationLock.release();
                        kubernetesService.deleteTask(task.taskId());
                        evaluationIdsToRunnables.remove(task.taskId());
                        notifyView(task.taskId());
                        this.failed = false;
                        podIndicesReadyToRun.remove(task.taskId());

                        if (isNoJobRunning()) {
                            dataManagerService.enableDataManagerSynchronization();
                            log.info("Enabled data manager synchronization");
                        }
                    }

                }
            } catch (final InterruptedException e) {
                log.info("Evaluation thread with id {} interrupted", Thread.currentThread().threadId());
                interrupted = true;
            } catch (final Exception e) {
                log.error("Evaluation thread with id {} stopped because of exception {}", Thread.currentThread().threadId(), e.getMessage());
                log.error("Evaluation thread will be restarted after 10 seconds");
                try {
                    Thread.sleep(10_000);
                    activeEvaluationThreads.add(taskExecutor.submit(this));
                } catch (final InterruptedException interruptedException) {
                    log.error("Evaluation thread with id {} interrupted while waiting for restart.", Thread.currentThread().threadId());
                    interrupted = true;
                }
            }

            log.info("Evaluation thread with id {} stopped", Thread.currentThread().threadId());
        }

    }

    @Transactional // TODO: @Transactional needed here?
    public void evaluationEventCallback(final EvaluationEvent result) {
        evaluationEventRepository.save(evaluationEventMapper.toEntity(result));
    }

    public synchronized void updateLogsAvailableAndNotifyView(final String jobId) {
        getSelfReference().updateLogsAvailable(jobId);
        this.notifyView(jobId);
    }

    public synchronized void updateResults(final String jobId, final String results) {
        final ResultsEvaluation resultsEvaluation = areResultsCorrect(results);
        getSelfReference().updateResults(resultsEvaluation, jobId);
        this.notifyView(jobId);
    }

    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    @Transactional
    public synchronized void updateLogsAvailable(final String taskId) {
        final Optional<EvaluationResultEntity> evaluationResultEntityOptional = this.evaluationResultRepository.findById(taskId);
        if (evaluationResultEntityOptional.isEmpty()) {
            return;
        }

        final EvaluationResultEntity evaluationResultEntity = evaluationResultEntityOptional.get();
        evaluationResultEntity.setLogsAvailable(true);
        evaluationResultRepository.save(evaluationResultEntity);
    }


    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    @Transactional
    public synchronized void updateResults(final ResultsEvaluation resultsEvaluation, final String jobId) {
        final Optional<EvaluationResultEntity> evaluationResultEntityOptional = this.evaluationResultRepository.findById(jobId);
        if (evaluationResultEntityOptional.isEmpty()) {
            return;
        }
        final EvaluationResultEntity evaluationResultEntity = evaluationResultEntityOptional.get();
        evaluationResultEntity.setResultsAvailable(true);
        evaluationResultEntity.setResultsCorrect(resultsEvaluation.correct());
        evaluationResultEntity.setResultProportion(resultsEvaluation.resultProportion());
        this.evaluationResultRepository.save(evaluationResultEntity);
    }

    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    @Transactional
    public synchronized void updateTaskStatus(final String taskId, final EvaluationStatus evaluationStatus) {
        final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
        evaluationResultEntity.setStatus(evaluationStatus);
        evaluationResultRepository.save(evaluationResultEntity);
    }

    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    @Transactional
    public synchronized void updateTaskStatusAndMessage(final String taskId, final EvaluationStatus evaluationStatus, String message) {
        final EvaluationResultEntity resultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
        resultEntity.setStatus(evaluationStatus);
        resultEntity.setMessage(message);
        evaluationResultRepository.save(resultEntity);
    }

    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    @Transactional
    public synchronized void setStartTimestampNow(final String taskId) {
        final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
        evaluationResultEntity.setStartTimestamp(ZonedDateTime.now());
        evaluationResultRepository.save(evaluationResultEntity);
    }

    // TODO: Check if both @Transactional and synchronized are needed here - this combination is not recommended
    @Transactional
    public synchronized void setEndTimestampNow(final String taskId) {
        final EvaluationResultEntity evaluationResultEntity = evaluationResultRepository.findById(taskId).orElseThrow();
        evaluationResultEntity.setEndTimestamp(ZonedDateTime.now());
        evaluationResultRepository.save(evaluationResultEntity);
    }

    public String findLastMostImportantErrorEvent(final String taskId) {
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

    public String getGrafanaLogsUrl(final EvaluationResultEntity evaluationResultEntity) {
        final String template = "http://%s/d/be2n0s0j623ggb/logs?orgId=1&from=%s&to=%s&timezone=browser&var-Filters=kubernetesPodName%%7C%%3D~%%7Cddm-akka-%s.%%2A&var-Filters=index%%7C%%3D%%7C0";

        return String.format(template, settingsService.getGrafanaHost(), getFromOrDefault(evaluationResultEntity), getToOrDefault(evaluationResultEntity), evaluationResultEntity.getTaskId());
    }

    public String getGrafanaResourcesUrl(final EvaluationResultEntity evaluationResultEntity) {
        final String template = "http://%s/d/ee9ya50i64u80c?orgId=1&from=%s&to=%s&tz=Europe%%2FZurich&theme=light&var-jobName=ddm-akka-%s.*";

        return String.format(template, settingsService.getGrafanaHost(), getFromOrDefault(evaluationResultEntity), getToOrDefault(evaluationResultEntity), evaluationResultEntity.getTaskId());
    }

    private static String getToOrDefault(EvaluationResultEntity evaluationResultEntity) {
        return Optional.ofNullable(evaluationResultEntity.getEndTimestamp()).map(ZonedDateTime::toInstant).map(Instant::toEpochMilli).map(String::valueOf).orElse("now");
    }

    private static String getFromOrDefault(EvaluationResultEntity evaluationResultEntity) {
        return Optional.ofNullable(evaluationResultEntity.getStartTimestamp()).map(ZonedDateTime::toInstant).map(Instant::toEpochMilli).map(String::valueOf).orElse("now-6h");
    }

    private void deploy(EvaluationTask task) {
        if (task instanceof GitEvaluationTask gitEvaluationTask) {
            kubernetesService.deployTask(gitEvaluationTask, settingsService, settingsService.getMaxJobsPerNode() > 1);
        }
    }

    public void notifyView(final String taskId) {
        activeEvaluationViewComponents.forEach(notifiableComponent -> notifiableComponent.dataChanged(taskId));
    }

    public void notifyView() {
        activeEvaluationViewComponents.forEach(NotifiableComponent::dataChanged);
    }

}
