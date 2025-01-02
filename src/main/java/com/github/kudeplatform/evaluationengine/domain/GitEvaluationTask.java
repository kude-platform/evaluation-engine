package com.github.kudeplatform.evaluationengine.domain;

import java.util.List;

/**
 * @author timo.buechert
 */
public class GitEvaluationTask extends EvaluationTask {

    private String gitUrl;

    private String gitBranch;

    public GitEvaluationTask(String gitUrl, String taskId, List<String> instanceStartCommands,
                             String name, String gitBranch, String datasetName) {
        super(taskId, instanceStartCommands, name, datasetName);
        this.gitUrl = gitUrl;
        this.gitBranch = gitBranch;
    }

    public String repositoryUrl() {
        return gitUrl;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String gitBranch() {
        return gitBranch;
    }

    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }
}
