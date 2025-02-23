package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapper;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static com.github.kudeplatform.evaluationengine.domain.EvaluationEvent.LEVEL_ERROR;
import static com.github.kudeplatform.evaluationengine.domain.EvaluationEvent.LEVEL_FATAL;

/**
 * @author timo.buechert
 */
@RestController
@Slf4j
@AllArgsConstructor
public class AlertController {

    private final EvaluationResultRepository evaluationResultRepository;

    private final EvaluationEventRepository evaluationEventRepository;

    private final EvaluationService evaluationService;

    private final EvaluationEventMapper evaluationEventMapper;

    /**
     * {
     * "version": "4",
     * "groupKey": <string>,              // key identifying the group of alerts (e.g. to deduplicate)
     * "truncatedAlerts": <int>,          // how many alerts have been truncated due to "max_alerts"
     * "status": "<resolved|firing>",
     * "receiver": <string>,
     * "groupLabels": <object>,
     * "commonLabels": <object>,
     * "commonAnnotations": <object>,
     * "externalURL": <string>,           // backlink to the Alertmanager.
     * "alerts": [
     * {
     * "status": "<resolved|firing>",
     * "labels": <object>,
     * "annotations": <object>,
     * "startsAt": "<rfc3339>",
     * "endsAt": "<rfc3339>",
     * "generatorURL": <string>,      // identifies the entity that caused the alert
     * "fingerprint": <string>        // fingerprint to identify the alert
     * },
     * ...
     * ]
     * }
     */

    @Data
    public static class AlertNotification {
        private String version;
        private String groupKey;
        private int truncatedAlerts;
        private String status;
        private String receiver;
        private Object groupLabels;
        private Object commonLabels;
        private Object commonAnnotations;
        private String externalURL;
        private Alert[] alerts;
    }

    @Data
    public static class Alert {
        private String status;
        private Map<String, Object> labels;
        private Map<String, Object> annotations;
        private String startsAt;
        private String endsAt;
        private String generatorURL;
        private String fingerprint;
    }

    @RequestMapping(value = "/api/alerts", method = RequestMethod.POST)
    @Transactional
    public void saveResults(@RequestBody final AlertNotification alertNotification) {
        log.debug("Received alert notification: {}", alertNotification);
        for (final Alert alert : alertNotification.getAlerts()) {
            log.debug("Received alert: {}", alert);
            final String evaluationId = Optional.ofNullable(alert.getLabels().get("label_evaluation_id")).map(Object::toString).orElse("");
            final String alertName = alert.getLabels().get("alertname").toString();

            if (!StringUtils.hasText(evaluationId) || !"firing".equals(alert.getStatus())) {
                continue;
            }

            if (alertName.equals("AllPodsLowCpuUsage")) {
                handleLowCpuUsageAlert(evaluationId, alert);
            }
            if (alertName.equals("OnePodLowCpuUsage")) {
                handleOnePodLowCpuUsageAlert(evaluationId, alert);
            }
        }
    }

    private void handleOnePodLowCpuUsageAlert(String evaluationId, Alert alert) {
        log.debug("Handling low CPU usage alert for evaluation {}", evaluationId);

        final Optional<EvaluationResultEntity> evaluationResultEntity = evaluationResultRepository.findById(evaluationId);
        if (evaluationResultEntity.isEmpty()) {
            return;
        }

        final EvaluationEvent evaluationEvent = new EvaluationEvent(evaluationId, ZonedDateTime.now(), EvaluationStatus.FAILED,
                (String) alert.getAnnotations().get("description"), "all", "LOW_CPU_USAGE_ON_ONE_INSTANCE", LEVEL_ERROR);
        this.evaluationEventRepository.save(evaluationEventMapper.toEntity(evaluationEvent));
    }

    private void handleLowCpuUsageAlert(final String evaluationId, final Alert alert) {
        log.debug("Handling low CPU usage alert for evaluation {}", evaluationId);

        final Optional<EvaluationResultEntity> evaluationResultEntity = evaluationResultRepository.findById(evaluationId);
        if (evaluationResultEntity.isEmpty()) {
            return;
        }

        final EvaluationEvent evaluationEvent = new EvaluationEvent(evaluationId, ZonedDateTime.now(), EvaluationStatus.FAILED,
                (String) alert.getAnnotations().get("description"), "all", "LOW_CPU_USAGE_ON_ALL_INSTANCES", LEVEL_FATAL);
        this.evaluationEventRepository.save(evaluationEventMapper.toEntity(evaluationEvent));

        this.evaluationService.failEvaluationTask(evaluationId, true);
    }


}
