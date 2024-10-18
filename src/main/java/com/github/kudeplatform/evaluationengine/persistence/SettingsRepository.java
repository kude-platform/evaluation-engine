package com.github.kudeplatform.evaluationengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author timo.buechert
 */
public interface SettingsRepository extends JpaRepository<SettingsEntity, Long> {

    SettingsEntity findBySettingsKey(String key);

}
