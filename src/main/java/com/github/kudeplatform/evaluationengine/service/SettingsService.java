package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.persistence.SettingsEntity;
import com.github.kudeplatform.evaluationengine.persistence.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author timo.buechert
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String KEY_TIMEOUT_IN_SECONDS = "timeoutInSeconds";

    private static final String KEY_REPLICATION_FACTOR = "replicationFactor";

    private final SettingsRepository settingsRepository;

    public int getTimeoutInSeconds() {
        return Integer.parseInt(getSetting(KEY_TIMEOUT_IN_SECONDS).orElse("600"));
    }

    public void setTimeoutInSeconds(final String timeoutInSeconds) {
        setSetting(KEY_TIMEOUT_IN_SECONDS, timeoutInSeconds);
    }

    public int getReplicationFactor() {
        return Integer.parseInt(getSetting(KEY_REPLICATION_FACTOR).orElse("2"));
    }

    public void setReplicationFactor(final String replicationFactor) {
        setSetting(KEY_REPLICATION_FACTOR, replicationFactor);
    }

    public Optional<String> getSetting(final String key) {
        return Optional.ofNullable(settingsRepository.findBySettingsKey(key)).map(SettingsEntity::getSettingsValue);
    }

    public void setSetting(final String key, final String value) {
        SettingsEntity settingsEntity = settingsRepository.findBySettingsKey(key);
        if (settingsEntity == null) {
            settingsEntity = new SettingsEntity();
            settingsEntity.setSettingsKey(key);
        }
        settingsEntity.setSettingsValue(value);
        settingsRepository.save(settingsEntity);
    }
}
