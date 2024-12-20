package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.Dataset;
import com.github.kudeplatform.evaluationengine.domain.EvaluationResultWithEvents;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapper;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapperImpl;
import com.github.kudeplatform.evaluationengine.persistence.DatasetEntity;
import com.github.kudeplatform.evaluationengine.persistence.DatasetRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

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

    public static final String KUDE_DATA_PATH = System.getProperty("user.home") + File.separator + "kude-data" + File.separator;

    private static final String[] CSV_HEADER = {
            "taskId", "name", "timestamp", "status", "logsAvailable", "resultsAvailable", "resultsCorrect", "message", "events"
    };

    private final DatasetRepository datasetRepository;

    private final DatasetMapper datasetMapper;

    public FileSystemService(final DatasetRepository datasetRepository) {
        this.datasetRepository = datasetRepository;
        this.datasetMapper = new DatasetMapperImpl();
    }

    @PostConstruct
    public void init() {
        final File kudeTmpFolder = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR);
        if (!kudeTmpFolder.exists() && !kudeTmpFolder.mkdirs()) {
            log.error("Failed to create KUDE tmp folder.");
            throw new RuntimeException("Failed to create KUDE tmp folder. Cannot continue.");
        }

        final File kudeDataFolder = new File(KUDE_DATA_PATH);
        if (!kudeDataFolder.exists() && !kudeDataFolder.mkdirs()) {
            log.error("Failed to create KUDE data folder.");
            throw new RuntimeException("Failed to create KUDE data folder. Cannot continue.");
        }

        this.initializeDatasetRepository();
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

    public void saveDataset(final InputStream in, final String fileName) {
        final File destination = new File(KUDE_DATA_PATH + fileName);

        if (destination.exists()) {
            log.error("The file {} already exists. Please delete it first.", fileName);
            throw new RuntimeException("The file " + fileName + " already exists. Please delete it first.");
        }

        try (FileOutputStream out = new FileOutputStream(destination)) {
            IOUtils.copy(in, out);
        } catch (IOException e) {
            log.error("Could not store the uploaded file. The error was: {}", e.getMessage());
            throw new RuntimeException("Could not store the uploaded file. The error was: " + e.getMessage());
        }

        datasetRepository.save(new DatasetEntity(null, fileName, destination.getAbsolutePath()));
    }

    public List<Dataset> getAvailableDatasets() {
        return datasetRepository.findAll().stream()
                .map(datasetMapper::toDomainObject)
                .toList();
    }

    private void initializeDatasetRepository() {
        final File dataDirectory = new File(KUDE_DATA_PATH);
        if (!dataDirectory.exists()) {
            log.error("The data directory {} does not exist.", KUDE_DATA_PATH);
            throw new RuntimeException("The data directory " + KUDE_DATA_PATH + " does not exist.");
        }

        final File[] files = dataDirectory.listFiles();
        if (files == null) {
            log.error("The data directory {} is empty.", KUDE_DATA_PATH);
            throw new RuntimeException("The data directory " + KUDE_DATA_PATH + " is empty.");
        }

        Stream.of(files)
                .filter(file -> file.getName().endsWith(".zip"))
                .map(file -> new Dataset(file.getName(), file.getAbsolutePath()))
                .forEach(dataset -> {
                    try {
                        datasetRepository.save(datasetMapper.toEntity(dataset));
                    } catch (Exception e) {
                        log.error("Could not save dataset {} to the database. The error was: {}", dataset.name(), e.getMessage());
                    }
                });
    }

    public void deleteDataset(final String name) {
        final DatasetEntity byName = this.datasetRepository.findByName(name);
        final File file = new File(byName.getPath());
        if (!file.delete()) {
            throw new RuntimeException("Could not delete dataset " + name);
        }
        this.datasetRepository.delete(byName);
    }
}
