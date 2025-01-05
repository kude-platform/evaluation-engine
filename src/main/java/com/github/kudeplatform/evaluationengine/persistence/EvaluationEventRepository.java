package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author timo.buechert
 */
public interface EvaluationEventRepository extends JpaRepository<EvaluationEventEntity, String> {

    List<EvaluationEventEntity> findByTaskId(String taskid);

    List<EvaluationEventEntity> findByTaskIdAndTypeAndIndex(String taskid, String type, String index);

    List<EvaluationEventEntity> findByTaskIdAndType(String taskid, String type);

    void deleteByTaskId(String taskid);
    
}
