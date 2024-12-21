package com.github.kudeplatform.evaluationengine.domain;

import java.util.List;

/**
 * @author timo.buechert
 */
public class GitEvaluationTask extends EvaluationTask {

    private String repositoryUrl;

    private String gitBranch;

    public GitEvaluationTask(String repositoryUrl, String taskId, List<String> instanceStartCommands,
                             String name, String gitBranch, String datasetName) {
        super(taskId, instanceStartCommands, name, datasetName);
        this.repositoryUrl = repositoryUrl;
        this.gitBranch = gitBranch;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String gitBranch() {
        return gitBranch;
    }

    public void setGitBranch(String gitBranch) {
        this.gitBranch = gitBranch;
    }
}
