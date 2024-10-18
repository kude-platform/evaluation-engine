package com.github.kudeplatform.evaluationengine.mapper;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventEntity;
import org.mapstruct.Mapper;

/**
 * @author timo.buechert
 */

@Mapper
public interface EvaluationEventMapper {

    EvaluationEvent toDomainObject(EvaluationEventEntity evaluationEventEntity);

    EvaluationEventEntity toEntity(EvaluationEvent evaluationEvent);

}
