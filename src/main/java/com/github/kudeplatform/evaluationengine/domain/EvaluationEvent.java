package com.github.kudeplatform.evaluationengine.domain;

import java.time.ZonedDateTime;

/**
 * @author timo.buechert
 */
public record EvaluationEvent(String taskId, ZonedDateTime timestamp, EvaluationStatus status, String message,
                              String index, String category) {

}
