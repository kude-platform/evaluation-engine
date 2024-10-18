package com.github.kudeplatform.evaluationengine.service;

/**
 * @author timo.buechert
 */
public class OrchestrationServiceException extends Exception {

    public OrchestrationServiceException(String message) {
        super(message);
    }

    public OrchestrationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
