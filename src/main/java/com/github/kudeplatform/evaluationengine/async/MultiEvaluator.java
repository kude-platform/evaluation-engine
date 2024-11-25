package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.domain.SingleEvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * @author timo.buechert
 */
@Component
@Slf4j
public class MultiEvaluator implements AsyncEvaluator {

    final ThreadPoolTaskExecutor taskExecutor;

    final List<SimpleEvaluator> sequentialPreconditionEvaluators;

    final List<SimpleEvaluator> parallelEvaluators;

    @Autowired
    public MultiEvaluator(final ThreadPoolTaskExecutor taskExecutor,
                          @Qualifier("preconditionEvaluator") final List<SimpleEvaluator> sequentialPreconditionEvaluators,
                          @Qualifier("parallelEvaluator") final List<SimpleEvaluator> evaluators) {
        this.taskExecutor = taskExecutor;
        this.sequentialPreconditionEvaluators = sequentialPreconditionEvaluators;
        this.parallelEvaluators = evaluators;
    }

    @Override
    public CompletableFuture<Result> evaluate(final EvaluationTask fileEvaluationTask,
                                              final Consumer<EvaluationEvent> updateCallback) {
        return CompletableFuture.supplyAsync(() -> this.evaluateAsync(fileEvaluationTask, updateCallback));
    }

    private Result evaluateAsync(final EvaluationTask evaluationTask,
                                 final Consumer<EvaluationEvent> updateCallback) {
        // first evaluate all sequential precondition evaluators
        for (final AsyncEvaluator evaluator : sequentialPreconditionEvaluators) {
            final Result result = evaluator.evaluate(evaluationTask, updateCallback).join();
            if (EvaluationStatus.FAILED.equals(result.getEvaluationStatus())) {
                return result;
            }
        }

        // then evaluate all parallel evaluators
        final List<CompletableFuture<Result>> completableFutures = new ArrayList<>();
        for (final AsyncEvaluator evaluator : parallelEvaluators) {
            completableFutures.add(evaluator.evaluate(evaluationTask, updateCallback));
        }

        try {
            allOfTerminateOnFailure(completableFutures.toArray(new CompletableFuture[0])).get();

            for (CompletableFuture<Result> future : completableFutures) {
                if (future.isCompletedExceptionally()) {
                    return new SingleEvaluationResult(evaluationTask, EvaluationStatus.FAILED, new ArrayList<>());
                }
                if (EvaluationStatus.FAILED.equals(future.get().getEvaluationStatus())) {
                    return future.get();
                }
            }

            return completableFutures.get(0).get();

        } catch (final ExecutionException | InterruptedException | CompletionException e) {
            log.error("Error while evaluating task: " + evaluationTask.taskId(), e);
            return new SingleEvaluationResult(evaluationTask, EvaluationStatus.FAILED, new ArrayList<>());
        }
    }

    public static CompletableFuture allOfTerminateOnFailure(CompletableFuture<?>... futures) {
        CompletableFuture<?> failure = new CompletableFuture();
        for (CompletableFuture<?> f : futures) {
            f.exceptionally(ex -> {
                failure.completeExceptionally(ex);
                return null;
            });
        }
        return CompletableFuture.anyOf(failure, CompletableFuture.allOf(futures));
    }


}
