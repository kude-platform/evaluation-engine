package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.EvaluationResultWithEvents;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * @author timo.buechert
 */
@Service
@Slf4j
public class FileSystemService {

    public static final String KUDE_TMP_FOLDER_NAME = "kude-tmp";

    public static final String KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR =
            System.getProperty("java.io.tmpdir") + File.separator + KUDE_TMP_FOLDER_NAME + File.separator;

    private static final String[] CSV_HEADER = {
            "taskId", "name", "timestamp", "status", "logsAvailable", "resultsAvailable", "resultsCorrect", "message", "events"
    };

    @PostConstruct
    public void init() {
        final File kudeTmpFolder = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR);
        if (!kudeTmpFolder.exists() && !kudeTmpFolder.mkdirs()) {
            log.error("Failed to create KUDE tmp folder.");
            throw new RuntimeException("Failed to create KUDE tmp folder. Cannot continue.");
        }
    }

    public void saveToCsvFile(final List<EvaluationResultWithEvents> evaluationResultWithEventsList) {
        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADER).setDelimiter(";").build();
        final Path path = Paths.get(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR + "export.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, TRUNCATE_EXISTING, WRITE, CREATE);
             CSVPrinter printer = new CSVPrinter(writer, format)) {

            for (final EvaluationResultWithEvents evaluationResultWithEvents : evaluationResultWithEventsList) {
                printer.printRecord((Object[]) serializeEvaluationResultWithEventsList(evaluationResultWithEvents));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String[] serializeEvaluationResultWithEventsList(final EvaluationResultWithEvents evaluationResultWithEvents) {
        return new String[]{
                evaluationResultWithEvents.getTaskId(),
                evaluationResultWithEvents.getName(),
                evaluationResultWithEvents.getTimestamp().toString(),
                evaluationResultWithEvents.getStatus().name(),
                String.valueOf(evaluationResultWithEvents.isLogsAvailable()),
                String.valueOf(evaluationResultWithEvents.isResultsAvailable()),
                String.valueOf(evaluationResultWithEvents.isResultsCorrect()),
                evaluationResultWithEvents.getMessage(),
                evaluationResultWithEvents.getEvents()
        };
    }
}
