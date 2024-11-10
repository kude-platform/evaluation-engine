package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public class EvaluationTask {

    private String taskId;

    private String name;

    private final String additionalCommandLineOptions;

    public EvaluationTask(String taskId, String additionalCommandLineOptions, String name) {
        this.taskId = taskId;
        this.additionalCommandLineOptions = additionalCommandLineOptions;
        this.name = name;
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

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
