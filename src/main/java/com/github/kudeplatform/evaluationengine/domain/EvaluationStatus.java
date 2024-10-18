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
    CANCELLED,
    TIMEOUT;

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isRunning() {
        return this == RUNNING || this == DEPLOYING;
    }

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

}
