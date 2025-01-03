package com.github.kudeplatform.evaluationengine.service;

/**
 * @author timo.buechert
 */
public enum KubernetesStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    TIMEOUT,
    UNKNOWN;

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isRunning() {
        return this == RUNNING;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    public boolean isSuccessful() {
        return this == SUCCEEDED;
    }
}
