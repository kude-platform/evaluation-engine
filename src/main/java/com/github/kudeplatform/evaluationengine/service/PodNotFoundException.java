package com.github.kudeplatform.evaluationengine.service;

/**
 * @author timo.buechert
 */
public class PodNotFoundException extends OrchestrationServiceException {
    public PodNotFoundException(String message) {
        super(message);
    }

    public PodNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
