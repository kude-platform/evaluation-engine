package com.github.kudeplatform.evaluationengine.async;

import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.service.KubernetesStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author timo.buechert
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EvaluationUtils {

    public static EvaluationStatus mapToEvaluationStatus(final KubernetesStatus jobStatus) {
        return switch (jobStatus) {
            case PENDING -> EvaluationStatus.PENDING;
            case RUNNING -> EvaluationStatus.RUNNING;
            case SUCCEEDED -> EvaluationStatus.SUCCEEDED;
            case FAILED, UNKNOWN -> EvaluationStatus.FAILED;
            case CANCELLED -> EvaluationStatus.CANCELLED;
            case TIMEOUT -> EvaluationStatus.TIMEOUT;
        };
    }
}
