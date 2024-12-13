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

    private static final String KEY_EXAMPLE_SOLUTION = "exampleSolution";

    private final SettingsRepository settingsRepository;

    private final KubernetesService kubernetesService;

    @PostConstruct
    public void init() {
        try {
            setReplicationFactor(String.valueOf(kubernetesService.getNumberOfNodes()));
            setExpectedSolution(createDefaultSampleSolution());
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

    public String getExpectedSolution() {
        return getSetting(KEY_EXAMPLE_SOLUTION).orElse("");
    }

    public void setExpectedSolution(final String sampleSolution) {
        setSetting(KEY_EXAMPLE_SOLUTION, sampleSolution);
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

    private String createDefaultSampleSolution() {
        return """
                tpch_nation -> tpch_nation: [N_REGIONKEY] c [N_NATIONKEY]
                tpch_supplier -> tpch_nation: [S_NATIONKEY] c [N_NATIONKEY]
                tpch_customer -> tpch_nation: [C_NATIONKEY] c [N_NATIONKEY]
                tpch_region -> tpch_nation: [R_REGIONKEY] c [N_NATIONKEY]
                tpch_region -> tpch_nation: [R_REGIONKEY] c [N_REGIONKEY]
                tpch_part -> tpch_supplier: [P_SIZE] c [S_SUPPKEY]
                tpch_part -> tpch_customer: [P_SIZE] c [C_CUSTKEY]
                tpch_nation -> tpch_supplier: [N_NATIONKEY] c [S_NATIONKEY]
                tpch_nation -> tpch_supplier: [N_REGIONKEY] c [S_NATIONKEY]
                tpch_customer -> tpch_supplier: [C_NATIONKEY] c [S_NATIONKEY]
                tpch_region -> tpch_supplier: [R_REGIONKEY] c [S_NATIONKEY]
                tpch_supplier -> tpch_customer: [S_SUPPKEY] c [C_CUSTKEY]
                tpch_nation -> tpch_customer: [N_NATIONKEY] c [C_NATIONKEY]
                tpch_nation -> tpch_customer: [N_REGIONKEY] c [C_NATIONKEY]
                tpch_supplier -> tpch_customer: [S_NATIONKEY] c [C_NATIONKEY]
                tpch_region -> tpch_customer: [R_REGIONKEY] c [C_NATIONKEY]
                tpch_nation -> tpch_region: [N_REGIONKEY] c [R_REGIONKEY]
                tpch_part -> tpch_part: [P_SIZE] c [P_PARTKEY]
                tpch_supplier -> tpch_part: [S_SUPPKEY] c [P_PARTKEY]
                tpch_customer -> tpch_part: [C_CUSTKEY] c [P_PARTKEY]
                """;
    }
}
