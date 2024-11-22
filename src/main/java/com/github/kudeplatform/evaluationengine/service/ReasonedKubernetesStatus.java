package com.github.kudeplatform.evaluationengine.service;

/**
 * @author timo.buechert
 */

public record ReasonedKubernetesStatus(KubernetesStatus status, String reason) {

}
