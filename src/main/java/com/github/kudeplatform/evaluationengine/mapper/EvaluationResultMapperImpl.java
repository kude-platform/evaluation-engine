package com.github.kudeplatform.evaluationengine.mapper;

import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;

/**
 * @author timo.buechert
 */
public class EvaluationResultMapperImpl implements EvaluationResultMapper {

    @Override
    public EvaluationResultEntity toEntity(final Result evaluationResult) {
        final EvaluationResultEntity evaluationResultEntity = new EvaluationResultEntity();
        evaluationResultEntity.setTaskId(evaluationResult.getEvaluationTask().taskId());
        evaluationResultEntity.setStatus(evaluationResult.getEvaluationStatus());
        return evaluationResultEntity;
    }

}
