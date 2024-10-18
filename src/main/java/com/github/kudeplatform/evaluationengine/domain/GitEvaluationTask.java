package com.github.kudeplatform.evaluationengine.domain;

import java.util.UUID;

/**
 * @author timo.buechert
 */
public class GitEvaluationTask extends EvaluationTask {

    private final String repositoryUrl;

    public GitEvaluationTask(String repositoryUrl, UUID taskId) {
        super(taskId);
        this.repositoryUrl = repositoryUrl;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }
}
