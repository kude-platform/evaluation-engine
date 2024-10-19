package com.github.kudeplatform.evaluationengine.domain;

import java.util.UUID;

/**
 * @author timo.buechert
 */
public class EvaluationTask {

    private UUID taskId;

    private final String additionalCommandLineOptions;

    public EvaluationTask(UUID taskId, String additionalCommandLineOptions) {
        this.taskId = taskId;
        this.additionalCommandLineOptions = additionalCommandLineOptions;
    }

    public UUID taskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public String additionalCommandLineOptions() {
        return additionalCommandLineOptions;
    }

}
