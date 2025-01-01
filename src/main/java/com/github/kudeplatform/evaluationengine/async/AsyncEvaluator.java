package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.Result;

import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author timo.buechert
 */
public interface AsyncEvaluator {

    Future<Result> evaluate(EvaluationTask evaluationTask, Consumer<EvaluationEvent> updateCallback, CompletionService<Result> completionService);

}
