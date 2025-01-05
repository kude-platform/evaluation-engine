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

    private String datasetName;

    private String masterStartCommand;

    private String firstWorkerStartCommand;

    private ZonedDateTime startTimestamp;

    private ZonedDateTime endTimestamp;

    private Integer durationInSeconds;

    private Integer netDurationInSeconds;

    private EvaluationStatus status;

    private Boolean logsAvailable;

    private Boolean resultsAvailable;

    private Boolean resultsCorrect;

    private String resultProportion;

    private String message;

    private String events;
}
