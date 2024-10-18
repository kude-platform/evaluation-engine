package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * @author timo.buechert
 */
public interface EvaluationResultRepository extends JpaRepository<EvaluationResultEntity, UUID> {


}
