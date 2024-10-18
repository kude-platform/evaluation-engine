package com.github.kudeplatform.evaluationengine.domain;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * @author timo.buechert
 */
public record EvaluationEvent(UUID taskId, ZonedDateTime timestamp, EvaluationStatus status, String message) {

}
