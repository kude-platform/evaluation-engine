package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public class GitEvaluationTask extends EvaluationTask {

    private String repositoryUrl;

    private String gitBranch;

    public GitEvaluationTask(String repositoryUrl, String taskId, String additionalCommandLineOptions, String name, String gitBranch, String datasetName) {
        super(taskId, additionalCommandLineOptions, name, datasetName);
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
