package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.EvaluationResultWithEvents;
import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author timo.buechert
 */
class FileSystemServiceTest {

    private final FileSystemService fileSystemService = new FileSystemService(null);

    @BeforeEach
    void setUp() {
        fileSystemService.init();
    }


    @Test
    void saveToCsvFile() throws Exception {
        //given
        final List<EvaluationResultWithEvents> evaluationResultWithEventsList = List.of(
                EvaluationResultWithEvents.builder()
                        .taskId("1")
                        .name("Test")
                        .gitUrl("https://github.com/test")
                        .gitBranch("main")
                        .datasetName("Dataset")
                        .masterStartCommand("Master")
                        .firstWorkerStartCommand("Worker")
                        .startTimestamp(ZonedDateTime.of(2024, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault()))
                        .endTimestamp(ZonedDateTime.of(2024, 1, 1, 1, 1, 30, 1, ZoneId.systemDefault()))
                        .durationInSeconds(29)
                        .netDurationInSeconds(10)
                        .status(EvaluationStatus.SUCCEEDED)
                        .logsAvailable(true)
                        .resultsAvailable(true)
                        .resultsCorrect(true)
                        .resultProportion("1/1")
                        .message("Test")
                        .events("Event1,Event2")
                        .build(),
                EvaluationResultWithEvents.builder()
                        .taskId("2")
                        .name("Test")
                        .gitUrl("https://github.com/test")
                        .gitBranch("main")
                        .datasetName(null)
                        .masterStartCommand("Master")
                        .firstWorkerStartCommand("Worker")
                        .startTimestamp(ZonedDateTime.of(2024, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault()))
                        .endTimestamp(ZonedDateTime.of(2024, 1, 1, 1, 1, 30, 1, ZoneId.systemDefault()))
                        .durationInSeconds(29)
                        .netDurationInSeconds(10)
                        .status(EvaluationStatus.FAILED)
                        .logsAvailable(true)
                        .resultsAvailable(true)
                        .resultsCorrect(true)
                        .resultProportion("1/1")
                        .message("Test2")
                        .events("Event1,Event2")
                        .build()
        );
        //when
        fileSystemService.saveToCsvFile(evaluationResultWithEventsList);

        //then
        final File resultFile = new File(FileSystemService.KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR + "export.csv");
        assertThat(resultFile).exists();

        final List<String> lines = Files.readAllLines(resultFile.toPath());
        assertThat(lines).hasSize(3);

        assertThat(lines.get(0)).isEqualTo("taskId;name;gitUrl;gitBranch;datasetName;masterStartCommand;firstWorkerStartCommand;startTimestamp;endTimestamp;durationInSeconds;netDurationInSeconds;status;logsAvailable;resultsAvailable;resultsCorrect;resultProportion;message;events");
        assertThat(lines.get(1)).isEqualTo("1;Test;https://github.com/test;main;Dataset;Master;Worker;2024-01-01T01:01:01.000000001+01:00[Europe/Berlin];2024-01-01T01:01:30.000000001+01:00[Europe/Berlin];29;10;SUCCEEDED;true;true;true;1/1;Test;Event1,Event2");
        assertThat(lines.get(2)).isEqualTo("2;Test;https://github.com/test;main;null;Master;Worker;2024-01-01T01:01:01.000000001+01:00[Europe/Berlin];2024-01-01T01:01:30.000000001+01:00[Europe/Berlin];29;10;FAILED;true;true;true;1/1;Test2;Event1,Event2");
        
        Files.delete(resultFile.toPath());
    }
}