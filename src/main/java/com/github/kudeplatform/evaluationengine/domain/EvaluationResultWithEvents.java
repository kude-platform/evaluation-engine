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

    private String gitUrl;

    private String gitBranch;

    private ZonedDateTime startTimestamp;

    private ZonedDateTime endTimestamp;

    private int durationInSeconds;

    private int netDurationInSeconds;

    private EvaluationStatus status;

    private boolean logsAvailable;

    private boolean resultsAvailable;

    private boolean resultsCorrect;

    private String resultProportion;

    private String message;

    private String events;
}
