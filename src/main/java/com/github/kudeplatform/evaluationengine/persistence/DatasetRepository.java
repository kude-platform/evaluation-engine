package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author timo.buechert
 */
public interface DatasetRepository extends JpaRepository<DatasetEntity, Long> {

    DatasetEntity findByName(String name);

}
