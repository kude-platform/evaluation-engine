package com.github.kudeplatform.evaluationengine.domain;

import lombok.AllArgsConstructor;

import java.util.List;

/**
 * @author timo.buechert
 */
@AllArgsConstructor
public class MultiEvaluationResult implements Result {

    private final List<Result> results;

    @Override
    public EvaluationTask getEvaluationTask() {
        return this.results.stream().findFirst().get().getEvaluationTask();
    }

    @Override
    public EvaluationStatus getEvaluationStatus() {
        return this.results.stream()
                .map(Result::getEvaluationStatus)
                .filter(status -> status == EvaluationStatus.FAILED)
                .findFirst()
                .orElse(EvaluationStatus.SUCCEEDED);
    }

    @Override
    public List<EvaluationEvent> getEvaluationEvents() {
        return this.results.stream().map(Result::getEvaluationEvents)
                .flatMap(List::stream)
                .toList();
    }
}
