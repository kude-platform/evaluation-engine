package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.persistence.SettingsEntity;
import com.github.kudeplatform.evaluationengine.persistence.SettingsRepository;
import io.kubernetes.client.openapi.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * @author timo.buechert
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private static final String KEY_TIMEOUT_IN_SECONDS = "timeoutInSeconds";

    private static final String KEY_REPLICATION_FACTOR = "replicationFactor";

    private static final String KEY_GIT_USERNAME = "gitUsername";

    private static final String KEY_GIT_TOKEN = "gitToken";

    private final SettingsRepository settingsRepository;

    private final KubernetesService kubernetesService;

    @PostConstruct
    public void init() {
        try {
            setReplicationFactor(String.valueOf(kubernetesService.getNumberOfNodes()));
        } catch (ApiException e) {
            log.error("Failed to get number of nodes from Kubernetes. Using default value.", e);
        }
    }

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

    public String getGitUsername() {
        return getSetting(KEY_GIT_USERNAME).orElse("");
    }

    public void setGitUsername(final String gitUsername) {
        setSetting(KEY_GIT_USERNAME, gitUsername);
    }

    public String getGitToken() {
        return getSetting(KEY_GIT_TOKEN).orElse("");
    }

    public void setGitToken(final String gitToken) {
        setSetting(KEY_GIT_TOKEN, gitToken);
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
