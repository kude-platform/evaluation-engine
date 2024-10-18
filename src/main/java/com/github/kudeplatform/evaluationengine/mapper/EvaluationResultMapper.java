package com.github.kudeplatform.evaluationengine.mapper;

import com.github.kudeplatform.evaluationengine.domain.Result;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;

/**
 * @author timo.buechert
 */

public interface EvaluationResultMapper {

    EvaluationResultEntity toEntity(Result evaluationResult);

}
