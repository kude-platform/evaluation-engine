package com.github.kudeplatform.evaluationengine.domain;

import java.time.ZonedDateTime;

/**
 * @author timo.buechert
 */
public record EvaluationEvent(String taskId, ZonedDateTime timestamp, EvaluationStatus status, String message,
                              String index, String type, String level) {

    public static final String LEVEL_INFO = "INFO";

    public static final String LEVEL_WARNING = "WARNING";

    public static final String LEVEL_ERROR = "ERROR";

    public static final String LEVEL_FATAL = "FATAL";

}
