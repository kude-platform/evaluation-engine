package com.github.kudeplatform.evaluationengine.domain;

import java.util.List;

/**
 * @author timo.buechert
 */
public class EvaluationTask {

    private String taskId;

    private String name;

    private final List<String> instanceStartCommands;

    private final String datasetName;

    public EvaluationTask(String taskId, List<String> instanceStartCommands, String name, String datasetName) {
        this.taskId = taskId;
        this.instanceStartCommands = instanceStartCommands;
        this.name = name;
        this.datasetName = datasetName;
    }

    public String taskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<String> instanceStartCommands() {
        return instanceStartCommands;
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
