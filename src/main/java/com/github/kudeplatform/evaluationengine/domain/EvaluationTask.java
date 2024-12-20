package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public class EvaluationTask {

    private String taskId;

    private String name;

    private final String additionalCommandLineOptions;

    private final String datasetName;

    public EvaluationTask(String taskId, String additionalCommandLineOptions, String name, String datasetName) {
        this.taskId = taskId;
        this.additionalCommandLineOptions = additionalCommandLineOptions;
        this.name = name;
        this.datasetName = datasetName;
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

    public String datasetName() {
        return datasetName;
    }

}
