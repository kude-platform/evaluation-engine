package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.persistence.SettingsEntity;
import com.github.kudeplatform.evaluationengine.persistence.SettingsRepository;
import io.kubernetes.client.openapi.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    private static final String KEY_MAX_JOBS_PER_NODE = "maxJobsPerNode";

    private static final String KEY_GIT_USERNAME = "gitUsername";

    private static final String KEY_GIT_TOKEN = "gitToken";

    private static final String KEY_EVALUATION_IMAGE = "evaluationImage";

    private static final String KEY_CPU_REQUEST = "cpuRequest";

    private static final String KEY_CPU_LIMIT = "cpuLimit";

    private static final String KEY_MEMORY_REQUEST = "memoryRequest";

    private static final String KEY_MEMORY_LIMIT = "memoryLimit";

    private static final String KEY_EXAMPLE_SOLUTION = "exampleSolution";

    private final SettingsRepository settingsRepository;

    private final KubernetesService kubernetesService;

    @Getter
    @Value("${PROMETHEUS_HOST:pi14.local:30103}")
    private String prometheusHost;

    @Getter
    @Value("${GRAFANA_HOST:pi14.local:32300}")
    private String grafanaHost;

    @Getter
    @Value("${USE_WATCH_TO_DETECT_COMPLETION:false}")
    private String useWatchToDetectCompletion;

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

    public int getMaxJobsPerNode() {
        return Integer.parseInt(getSetting(KEY_MAX_JOBS_PER_NODE).orElse("1"));
    }

    public void setMaxJobsPerNode(final String maxJobsPerNode) {
        setSetting(KEY_MAX_JOBS_PER_NODE, maxJobsPerNode);
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

    public String getEvaluationImage() {
        return getSetting(KEY_EVALUATION_IMAGE).orElse("registry.local/akka-tpch-jdk11:0.4.10");
    }

    public void setEvaluationImage(final String evaluationImage) {
        setSetting(KEY_EVALUATION_IMAGE, evaluationImage);
    }

    public String getCpuRequest() {
        return getSetting(KEY_CPU_REQUEST).orElse("3");
    }

    public void setCpuRequest(final String cpuRequest) {
        setSetting(KEY_CPU_REQUEST, cpuRequest);
    }

    public String getCpuLimit() {
        return getSetting(KEY_CPU_LIMIT).orElse("3");
    }

    public void setCpuLimit(final String cpuLimit) {
        setSetting(KEY_CPU_LIMIT, cpuLimit);
    }

    public String getMemoryRequest() {
        return getSetting(KEY_MEMORY_REQUEST).orElse("2048Mi");
    }

    public void setMemoryRequest(final String memoryRequest) {
        setSetting(KEY_MEMORY_REQUEST, memoryRequest);
    }

    public String getMemoryLimit() {
        return getSetting(KEY_MEMORY_LIMIT).orElse("2548Mi");
    }

    public void setMemoryLimit(final String memoryLimit) {
        setSetting(KEY_MEMORY_LIMIT, memoryLimit);
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
                WDC_astronomical -> WDC_age: [Name] c [Planet]
                WDC_game -> WDC_age: [DivisionName] c [Planet]
                WDC_kepler -> WDC_age: [Planet] c [Planet]
                WDC_planets -> WDC_age: [Name] c [Planet]
                WDC_satellites -> WDC_age: [Planet] c [Planet]
                WDC_age -> WDC_appearances: [Planet] c [Planet]
                WDC_astronomical -> WDC_appearances: [Name] c [Planet]
                WDC_game -> WDC_appearances: [DivisionName] c [Planet]
                WDC_kepler -> WDC_appearances: [Planet] c [Planet]
                WDC_planets -> WDC_appearances: [Name] c [Planet]
                WDC_satellites -> WDC_appearances: [Planet] c [Planet]
                WDC_astrology -> WDC_astrology: [Domicile] c [Detriment]
                WDC_astrology -> WDC_astrology: [Planetary Joy] c [Detriment]
                WDC_astrology -> WDC_astrology: [Detriment] c [Domicile]
                WDC_astrology -> WDC_astrology: [Planetary Joy] c [Domicile]
                WDC_astrology -> WDC_astrology: [Fall] c [Exaltation]
                WDC_astrology -> WDC_astrology: [Planetary Joy] c [Exaltation]
                WDC_astrology -> WDC_astrology: [Exaltation] c [Fall]
                WDC_astrology -> WDC_astrology: [Planetary Joy] c [Fall]
                WDC_game -> WDC_astronomical: [DivisionName] c [Name]
                WDC_planets -> WDC_astronomical: [Name] c [Name]
                WDC_satellites -> WDC_astronomical: [Planet] c [Name]
                WDC_astronomical -> WDC_game: [Name] c [DivisionName]
                WDC_planets -> WDC_game: [Name] c [DivisionName]
                WDC_satellites -> WDC_game: [Planet] c [DivisionName]
                WDC_game -> WDC_game: [LowOrbitLeaderBoardPrizePool] c [HighOrbitLeaderBoardPrizePool]
                WDC_game -> WDC_game: [HighOrbitLeaderBoardPrizePool] c [LowOrbitLeaderBoardPrizePool]
                WDC_age -> WDC_kepler: [Planet] c [Planet]
                WDC_astronomical -> WDC_kepler: [Name] c [Planet]
                WDC_game -> WDC_kepler: [DivisionName] c [Planet]
                WDC_planets -> WDC_kepler: [Name] c [Planet]
                WDC_satellites -> WDC_kepler: [Planet] c [Planet]
                WDC_astronomical -> WDC_planets: [Name] c [Name]
                WDC_game -> WDC_planets: [DivisionName] c [Name]
                WDC_satellites -> WDC_planets: [Planet] c [Name]
                WDC_age -> WDC_planetz: [Planet] c [Planet]
                WDC_astronomical -> WDC_planetz: [Name] c [Planet]
                WDC_game -> WDC_planetz: [DivisionName] c [Planet]
                WDC_kepler -> WDC_planetz: [Planet] c [Planet]
                WDC_planets -> WDC_planetz: [Name] c [Planet]
                WDC_satellites -> WDC_planetz: [Planet] c [Planet]
                WDC_astronomical -> WDC_science: [Name] c [Object]
                WDC_game -> WDC_science: [DivisionName] c [Object]
                WDC_planets -> WDC_science: [Name] c [Object]
                WDC_satellites -> WDC_science: [Planet] c [Object]
                WDC_age -> WDC_symbols: [Planet] c [Symbol]
                WDC_astrology -> WDC_symbols: [Detriment] c [Symbol]
                WDC_astrology -> WDC_symbols: [Domicile] c [Symbol]
                WDC_astrology -> WDC_symbols: [Planetary Joy] c [Symbol]
                WDC_astrology -> WDC_symbols: [Sign] c [Symbol]
                WDC_astronomical -> WDC_symbols: [Name] c [Symbol]
                WDC_game -> WDC_symbols: [DivisionName] c [Symbol]
                WDC_kepler -> WDC_symbols: [Planet] c [Symbol]
                WDC_planets -> WDC_symbols: [Name] c [Symbol]
                WDC_satellites -> WDC_symbols: [Planet] c [Symbol]
                WDC_science -> WDC_symbols: [Object] c [Symbol]
                                
                """;
    }
}
