package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public enum EvaluationStatus {
    PENDING,
    DEPLOYING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
