package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public class GitEvaluationTask extends EvaluationTask {

    private String repositoryUrl;

    public GitEvaluationTask(String repositoryUrl, String taskId, String additionalCommandLineOptions) {
        super(taskId, additionalCommandLineOptions);
        this.repositoryUrl = repositoryUrl;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }
    
    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}
