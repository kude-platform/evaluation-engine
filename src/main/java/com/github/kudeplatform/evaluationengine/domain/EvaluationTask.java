package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public class EvaluationTask {

    private String taskId;

    private final String additionalCommandLineOptions;

    public EvaluationTask(String taskId, String additionalCommandLineOptions) {
        this.taskId = taskId;
        this.additionalCommandLineOptions = additionalCommandLineOptions;
    }

    public String taskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String additionalCommandLineOptions() {
        return additionalCommandLineOptions;
    }

}
