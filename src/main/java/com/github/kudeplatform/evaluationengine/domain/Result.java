package com.github.kudeplatform.evaluationengine.domain;

import java.util.List;

/**
 * @author timo.buechert
 */
public interface Result {

    EvaluationTask getEvaluationTask();

    EvaluationStatus getEvaluationStatus();

    List<EvaluationEvent> getEvaluationEvents();

}
