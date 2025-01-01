package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * @author timo.buechert
 */
@Component
@Slf4j
public class MultiEvaluator implements AsyncEvaluator {


    final List<SimpleEvaluator> parallelEvaluators;

    final ExecutorService executorService;

    @Autowired
    public MultiEvaluator(@Qualifier("parallelEvaluator") final List<SimpleEvaluator> evaluators,
                          @Qualifier("asyncEvaluatorExecutorService") final ExecutorService executorService) {
        this.parallelEvaluators = evaluators;
        this.executorService = executorService;
    }

    @Override
    public Future<Result> evaluate(final EvaluationTask fileEvaluationTask,
                                   final Consumer<EvaluationEvent> updateCallback,
                                   final CompletionService<Result> completionService) {
        return completionService.submit(() -> this.evaluateAsync(fileEvaluationTask, updateCallback));
    }

    private Result evaluateAsync(final EvaluationTask evaluationTask,
                                 final Consumer<EvaluationEvent> updateCallback) {

        final List<Future<Result>> futures = new ArrayList<>();
        final ExecutorCompletionService<Result> completionService = new ExecutorCompletionService<>(executorService);
        for (final AsyncEvaluator asyncEvaluator : parallelEvaluators) {
            futures.add(asyncEvaluator.evaluate(evaluationTask, updateCallback, completionService));
        }

        try {
            int received = 0;
            List<Result> results = new ArrayList<>();
            while (received < parallelEvaluators.size()) {
                final Future<Result> resultFuture = completionService.take();
                try {
                    final Result result = resultFuture.get();

                    if (EvaluationStatus.FAILED.equals(result.getEvaluationStatus())) {
                        return result;
                    }
                    received++;
                    results.add(result);
                } catch (final CancellationException cancellationException) {
                    log.debug("MultiEvaluator cancelled. Now cancelling dependent futures.");
                    futures.forEach(cf -> cf.cancel(true));
                    return new SingleEvaluationResult(evaluationTask, EvaluationStatus.CANCELLED, new ArrayList<>());
                } catch (final Exception e) {
                    log.error("Error while evaluating task: {}", evaluationTask.taskId(), e);
                    return new SingleEvaluationResult(evaluationTask, EvaluationStatus.FAILED, new ArrayList<>());
                }

            }
            return results.get(0);

        } catch (final InterruptedException e) {
            log.debug("MultiEvaluator interrupted. Now cancelling dependent futures.");
            futures.forEach(cf -> cf.cancel(true));
            return new SingleEvaluationResult(evaluationTask, EvaluationStatus.FAILED, new ArrayList<>());
    }
    }
}
