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

    private final SettingsRepository settingsRepository;

    public long getTimeoutInSeconds() {
        return Long.parseLong(getSetting("timeoutInSeconds").orElse("600"));
    }

    public void setTimeoutInSeconds(final String timeoutInSeconds) {
        setSetting("timeoutInSeconds", timeoutInSeconds);
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
