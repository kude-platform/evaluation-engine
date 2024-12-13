package com.github.kudeplatform.evaluationengine.domain;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

/**
 * @author timo.buechert
 */
@Data
@Builder
public class EvaluationResultWithEvents {

    private String taskId;

    private String name;

    private ZonedDateTime timestamp;

    private EvaluationStatus status;

    private boolean logsAvailable;

    private boolean resultsAvailable;

    private boolean resultsCorrect;

    private String message;

    private String events;
}
