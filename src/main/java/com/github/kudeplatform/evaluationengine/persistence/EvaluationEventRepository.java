package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author timo.buechert
 */
public interface EvaluationEventRepository extends JpaRepository<EvaluationEventEntity, String> {

    List<EvaluationEventEntity> findByTaskId(String taskid);

    List<EvaluationEventEntity> findByTaskIdAndCategoryAndIndex(String taskid, String category, String index);

    void deleteByTaskId(String taskid);
    
}
