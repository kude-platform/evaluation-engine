package com.github.kudeplatform.evaluationengine.domain;

import java.util.UUID;

/**
 * @author timo.buechert
 */
public class EvaluationTask {

    private final UUID taskId;

    public EvaluationTask(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID taskId() {
        return taskId;
    }
}
