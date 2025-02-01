package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.SupportedModes;
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

    private static final String KEY_MODE = "mode";

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
    @Value("${GRAFANA_USER:admin}")
    private String grafanaUser;

    @Getter
    @Value("${GRAFANA_PASSWORD:admin}")
    private String grafanaPassword;

    @PostConstruct
    public void init() {
        try {
            setReplicationFactor(String.valueOf(kubernetesService.getNumberOfNodes()));
            setExpectedSolution(createDefaultSampleSolution());
        } catch (ApiException e) {
            log.error("Failed to get number of nodes from Kubernetes. Using default value.", e);
        }
    }


    public SupportedModes getMode() {
        try {
            return SupportedModes.fromString(getSetting(KEY_MODE).orElse("spark").toUpperCase());
        } catch (Exception e) {
            log.error("Failed to parse mode from settings. Using default value.", e);
            return SupportedModes.SPARK;
        }
    }

    public void setMode(final String mode) {
        setSetting(KEY_MODE, mode);
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
        return getSetting(KEY_EVALUATION_IMAGE).orElse("registry.local/ddm-spark:0.0.14");
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
        return getSetting(KEY_MEMORY_REQUEST).orElse("3048Mi");
    }

    public void setMemoryRequest(final String memoryRequest) {
        setSetting(KEY_MEMORY_REQUEST, memoryRequest);
    }

    public String getMemoryLimit() {
        return getSetting(KEY_MEMORY_LIMIT).orElse("3048Mi");
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
                tpch_supplier -> tpch_customer: [S_SUPPKEY] c [C_CUSTKEY]
                tpch_supplier -> tpch_customer: [S_NATIONKEY] c [C_NATIONKEY]
                tpch_supplier -> tpch_lineitem: [S_SUPPKEY] c [L_SUPPKEY]
                tpch_supplier -> tpch_nation: [S_NATIONKEY] c [N_NATIONKEY]
                tpch_supplier -> tpch_part: [S_SUPPKEY] c [P_PARTKEY]
                tpch_customer -> tpch_supplier: [C_NATIONKEY] c [S_NATIONKEY]
                tpch_customer -> tpch_nation: [C_NATIONKEY] c [N_NATIONKEY]
                tpch_lineitem -> tpch_supplier: [L_LINENUMBER] c [S_SUPPKEY]
                tpch_lineitem -> tpch_supplier: [L_SUPPKEY] c [S_SUPPKEY]
                tpch_lineitem -> tpch_supplier: [L_LINENUMBER] c [S_NATIONKEY]
                tpch_customer -> tpch_part: [C_CUSTKEY] c [P_PARTKEY]
                tpch_lineitem -> tpch_customer: [L_LINENUMBER] c [C_CUSTKEY]
                tpch_lineitem -> tpch_customer: [L_SUPPKEY] c [C_CUSTKEY]
                tpch_lineitem -> tpch_customer: [L_LINENUMBER] c [C_NATIONKEY]
                tpch_lineitem -> tpch_lineitem: [L_LINENUMBER] c [L_PARTKEY]
                tpch_lineitem -> tpch_lineitem: [L_LINENUMBER] c [L_SUPPKEY]
                tpch_lineitem -> tpch_lineitem: [L_TAX] c [L_DISCOUNT]
                tpch_lineitem -> tpch_lineitem: [L_COMMIT] c [L_RECEIPT]
                tpch_lineitem -> tpch_lineitem: [L_COMMIT] c [L_SHIP]
                tpch_lineitem -> tpch_nation: [L_LINENUMBER] c [N_NATIONKEY]
                tpch_lineitem -> tpch_orders: [L_LINENUMBER] c [O_ORDERKEY]
                tpch_lineitem -> tpch_orders: [L_LINESTATUS] c [O_ORDERSTATUS]
                tpch_lineitem -> tpch_part: [L_LINENUMBER] c [P_PARTKEY]
                tpch_lineitem -> tpch_orders: [L_ORDERKEY] c [O_ORDERKEY]
                tpch_lineitem -> tpch_part: [L_LINENUMBER] c [P_SIZE]
                tpch_lineitem -> tpch_part: [L_SUPPKEY] c [P_PARTKEY]
                tpch_region -> tpch_supplier: [R_REGIONKEY] c [S_NATIONKEY]
                tpch_region -> tpch_customer: [R_REGIONKEY] c [C_NATIONKEY]
                tpch_region -> tpch_nation: [R_REGIONKEY] c [N_NATIONKEY]
                tpch_region -> tpch_nation: [R_REGIONKEY] c [N_REGIONKEY]
                tpch_nation -> tpch_supplier: [N_NATIONKEY] c [S_NATIONKEY]
                tpch_nation -> tpch_supplier: [N_REGIONKEY] c [S_NATIONKEY]
                tpch_nation -> tpch_customer: [N_NATIONKEY] c [C_NATIONKEY]
                tpch_nation -> tpch_customer: [N_REGIONKEY] c [C_NATIONKEY]
                tpch_nation -> tpch_region: [N_REGIONKEY] c [R_REGIONKEY]
                tpch_lineitem -> tpch_part: [L_PARTKEY] c [P_PARTKEY]
                tpch_nation -> tpch_nation: [N_REGIONKEY] c [N_NATIONKEY]
                tpch_orders -> tpch_supplier: [O_SHIPPRIORITY] c [S_NATIONKEY]
                tpch_orders -> tpch_customer: [O_SHIPPRIORITY] c [C_NATIONKEY]
                tpch_orders -> tpch_customer: [O_CUSTKEY] c [C_CUSTKEY]
                tpch_orders -> tpch_region: [O_SHIPPRIORITY] c [R_REGIONKEY]
                tpch_orders -> tpch_nation: [O_SHIPPRIORITY] c [N_REGIONKEY]
                tpch_orders -> tpch_nation: [O_SHIPPRIORITY] c [N_NATIONKEY]
                tpch_orders -> tpch_part: [O_CUSTKEY] c [P_PARTKEY]
                tpch_part -> tpch_supplier: [P_SIZE] c [S_SUPPKEY]
                tpch_part -> tpch_customer: [P_SIZE] c [C_CUSTKEY]
                tpch_part -> tpch_lineitem: [P_SIZE] c [L_PARTKEY]
                tpch_part -> tpch_lineitem: [P_SIZE] c [L_SUPPKEY]
                tpch_part -> tpch_part: [P_SIZE] c [P_PARTKEY]
                                
                """;
    }

}
