package com.github.kudeplatform.evaluationengine.service;

import io.kubernetes.client.openapi.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataManagerService {

    private static final String DATA_MANAGER_POD_NAME = "data-manager";

    private final KubernetesService kubernetesService;

    @Value("${DATA_MANAGER_PORT:8090}")
    private String dataManagerPort;

    public void disableDataManagerSynchronization() {
        performGet("disableUpdateMechanism");
    }

    public void enableDataManagerSynchronization() {
        performGet("enableUpdateMechanism");
    }

    private void performGet(final String uri) {
        List<String> ipAdressesOfPods = List.of();

        try {
            ipAdressesOfPods = kubernetesService.getIpAdressesOfPods(DATA_MANAGER_POD_NAME);
        } catch (final ApiException e) {
            log.error("Error occured while trying to get ip adresses of data-manager", e);
        }

        final RestClient restClient = RestClient.builder().build();

        for (final String ipAdress : ipAdressesOfPods) {
            try {
                restClient.get().uri(String.format("http://%s:%s/%s", ipAdress, dataManagerPort, uri)).retrieve().toBodilessEntity();
            } catch (final Exception e) {
                log.error("Error occured while trying to call data-manager at ip {}", ipAdress, e);
            }
        }
    }
}
