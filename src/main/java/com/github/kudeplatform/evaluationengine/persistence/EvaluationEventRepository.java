package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * @author timo.buechert
 */
public interface EvaluationEventRepository extends JpaRepository<EvaluationEventEntity, String> {

    List<EvaluationEventEntity> findByTaskId(UUID taskid);

    List<EvaluationEventEntity> findByTaskIdAndCategory(UUID taskid, String category);

    List<EvaluationEventEntity> findByTaskIdAndCategoryAndIndex(UUID taskid, String category, String index);
    
}
