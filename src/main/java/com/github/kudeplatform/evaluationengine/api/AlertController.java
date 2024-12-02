package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import com.github.kudeplatform.evaluationengine.mapper.EvaluationEventMapper;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * @author timo.buechert
 */
@RestController
@Slf4j
public class AlertController {

    private final EvaluationResultRepository evaluationResultRepository;

    private final EvaluationEventRepository evaluationEventRepository;

    private final EvaluationService evaluationService;

    private final EvaluationEventMapper evaluationEventMapper;

    @Autowired
    public AlertController(final EvaluationResultRepository evaluationResultRepository,
                           final EvaluationEventRepository evaluationEventRepository,
                           final EvaluationService evaluationService,
                           final EvaluationEventMapper evaluationEventMapper) {
        this.evaluationResultRepository = evaluationResultRepository;
        this.evaluationEventRepository = evaluationEventRepository;
        this.evaluationService = evaluationService;
        this.evaluationEventMapper = evaluationEventMapper;
    }

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
    public void saveResults(@RequestBody final AlertNotification alertNotification) {
        log.info("Received alert notification: {}", alertNotification);
        for (final Alert alert : alertNotification.getAlerts()) {
            final String evaluationId = Optional.ofNullable(alert.getLabels().get("label_evaluation_id")).map(Object::toString).orElse("");
            final String alertName = alert.getLabels().get("alertname").toString();

            if (!StringUtils.hasText(evaluationId)) {
                log.warn("No evaluation ID found in alert {}", alert);
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
        log.info("Handling low CPU usage alert for evaluation {}", evaluationId);

        final Optional<EvaluationResultEntity> evaluationResultEntity = evaluationResultRepository.findById(evaluationId);
        if (evaluationResultEntity.isEmpty()) {
            log.error("Evaluation result with ID {} not found", evaluationId);
            return;
        }

        final EvaluationEvent evaluationEvent = new EvaluationEvent(evaluationId, ZonedDateTime.now(), EvaluationStatus.FAILED,
                (String) alert.getAnnotations().get("description"), "all", "LOW_CPU_USAGE_ON_ONE_INSTANCE");
        this.evaluationEventRepository.save(evaluationEventMapper.toEntity(evaluationEvent));
    }

    private void handleLowCpuUsageAlert(final String evaluationId, final Alert alert) {
        log.info("Handling low CPU usage alert for evaluation {}", evaluationId);

        final Optional<EvaluationResultEntity> evaluationResultEntity = evaluationResultRepository.findById(evaluationId);
        if (evaluationResultEntity.isEmpty()) {
            log.error("Evaluation result with ID {} not found", evaluationId);
            return;
        }

        final EvaluationResultEntity entity = evaluationResultEntity.get();

        final EvaluationEvent evaluationEvent = new EvaluationEvent(evaluationId, ZonedDateTime.now(), EvaluationStatus.FAILED,
                (String) alert.getAnnotations().get("description"), "all", "LOW_CPU_USAGE_ON_ALL_INSTANCES");
        this.evaluationEventRepository.save(evaluationEventMapper.toEntity(evaluationEvent));

        this.evaluationService.cancelEvaluationTask(evaluationId, false);

        entity.setStatus(EvaluationStatus.FAILED);
        this.evaluationResultRepository.save(entity);
        this.evaluationService.notifyView();
    }


}
