package com.github.kudeplatform.evaluationengine.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author timo.buechert
 */
@Mapper
public interface DatasetMapper {

    com.github.kudeplatform.evaluationengine.domain.Dataset toDomainObject(com.github.kudeplatform.evaluationengine.persistence.DatasetEntity datasetEntity);

    @Mapping(target = "id", ignore = true)
    com.github.kudeplatform.evaluationengine.persistence.DatasetEntity toEntity(com.github.kudeplatform.evaluationengine.domain.Dataset dataset);

}
