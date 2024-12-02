package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author timo.buechert
 */
public interface ErrorEventDefinitionRepository extends JpaRepository<ErrorEventDefinitionEntity, Long> {
}
