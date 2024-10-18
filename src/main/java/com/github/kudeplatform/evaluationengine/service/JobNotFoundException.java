package com.github.kudeplatform.evaluationengine.service;

/**
 * @author timo.buechert
 */
public class JobNotFoundException extends OrchestrationServiceException {
    public JobNotFoundException(String message) {
        super(message);
    }

    public JobNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
