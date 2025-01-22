package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ImageExportService {

    final EvaluationService evaluationService;

    final FileSystemService fileSystemService;

    final SettingsService settingsService;

    public ImageExportService(final EvaluationService evaluationService, final FileSystemService fileSystemService, final SettingsService settingsService) {
        this.evaluationService = evaluationService;
        this.fileSystemService = fileSystemService;
        this.settingsService = settingsService;
    }

    public List<String> getImageDownloadLinksForBash() {
        final List<EvaluationResultEntity> finishedEvaluationEntities = this.evaluationService.getFinishedEvaluationEntities();

        final List<String> downloadLinks = new java.util.ArrayList<>();
        for (final EvaluationResultEntity finishedEvaluationResultEntity : finishedEvaluationEntities) {
            if (finishedEvaluationResultEntity.getStartTimestamp() == null || finishedEvaluationResultEntity.getEndTimestamp() == null) {
                log.error("EvaluationResultEntity with taskId {} has no start or end timestamp. Cannot export image", finishedEvaluationResultEntity.getTaskId());
                continue;
            }

            final String grafanaChartRendererUrl = this.getGrafanaChartRendererUrl(finishedEvaluationResultEntity);
            downloadLinks.add(String.format("curl \"%s\" -o %s", grafanaChartRendererUrl, finishedEvaluationResultEntity.getTaskId() + ".png"));
        }

        return downloadLinks;

    }

    private String getGrafanaChartRendererUrl(final EvaluationResultEntity evaluationResultEntity) {
        final String grafanaChartRendererUrlTemplate = "http://%s:%s@%s/render/d/ee9ya50i64u80c?orgId=1&from=%d&to=%d&width=1080&height=700&tz=Europe%%2FZurich&theme=light&var-jobName=ddm-akka-%s.*&kiosk";
        final long startTimestamp = evaluationResultEntity.getStartTimestamp().toInstant().toEpochMilli();
        final long endTimestamp = evaluationResultEntity.getEndTimestamp().toInstant().toEpochMilli();

        return String.format(grafanaChartRendererUrlTemplate, settingsService.getGrafanaUser(), settingsService.getGrafanaPassword(), settingsService.getGrafanaHost(), startTimestamp, endTimestamp, evaluationResultEntity.getTaskId());
    }


}
