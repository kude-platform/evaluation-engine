package com.github.kudeplatform.evaluationengine.domain;

import lombok.AllArgsConstructor;

import java.util.List;

/**
 * @author timo.buechert
 */
@AllArgsConstructor
public class SingleEvaluationResult implements Result {

    private EvaluationTask task;

    private EvaluationStatus status;

    private List<EvaluationEvent> evaluationEvents;

    @Override
    public EvaluationTask getEvaluationTask() {
        return this.task;
    }

    @Override
    public EvaluationStatus getEvaluationStatus() {
        return this.status;
    }

    @Override
    public List<EvaluationEvent> getEvaluationEvents() {
        return this.evaluationEvents;
    }
}
