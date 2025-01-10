package com.github.kudeplatform.evaluationengine.service;

import com.github.kudeplatform.evaluationengine.domain.Dataset;
import com.github.kudeplatform.evaluationengine.domain.EvaluationResultWithEvents;
import com.github.kudeplatform.evaluationengine.domain.Repository;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapper;
import com.github.kudeplatform.evaluationengine.mapper.DatasetMapperImpl;
import com.github.kudeplatform.evaluationengine.persistence.DatasetEntity;
import com.github.kudeplatform.evaluationengine.persistence.DatasetRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

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

    public static final String KUDE_PLAGIARISM_PATH = System.getProperty("user.home") + File.separator + "kude-plagiarism" + File.separator;

    public static final String KUDE_SUBMISSIONS_PATH = System.getProperty("java.io.tmpdir") + File.separator + "kude-submissions" + File.separator;

    public static final String KUDE_PLAGIARISM_RESULTS_FILE = "kude-plagiarism-results.zip";

    public static final String KUDE_PLAGIARISM_RESULTS_PATH = KUDE_PLAGIARISM_PATH + KUDE_PLAGIARISM_RESULTS_FILE;

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

        final File kudeSubmissionsFolder = new File(KUDE_SUBMISSIONS_PATH);
        if (!kudeSubmissionsFolder.exists() && !kudeSubmissionsFolder.mkdirs()) {
            log.error("Failed to create KUDE submissions folder.");
            throw new RuntimeException("Failed to create KUDE submissions folder. Cannot continue.");
        }

        this.initializeDatasetRepository();
    }

    public void saveToCsvFile(final List<EvaluationResultWithEvents> evaluationResultWithEventsList) {
        final String[] headers = Arrays.stream(EvaluationResultWithEvents.class.getDeclaredFields()).map(Field::getName).toArray(String[]::new);

        final CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers).setDelimiter(";").build();
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
        final Field[] fields = EvaluationResultWithEvents.class.getDeclaredFields();
        final String[] result = new String[fields.length];

        for (int i = 0; i < fields.length; i++) {
            final Field field = fields[i];
            try {
                field.setAccessible(true);
                result[i] = String.valueOf(field.get(evaluationResultWithEvents));
            } catch (final Exception e) {
                log.error("Could not serialize field {}. The error was: {}", field.getName(), e.getMessage());
                result[i] = "Serialization error";
            }
        }

        return result;
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

        datasetRepository.save(new DatasetEntity(null, getDatasetName(fileName), fileName, destination.getAbsolutePath()));
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
                .map(file -> new Dataset(getDatasetName(file.getName()), file.getName(), file.getAbsolutePath()))
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

    private String getDatasetName(final String fileName) {
        return fileName.substring(fileName.lastIndexOf('/') + 1, fileName.lastIndexOf('.'));
    }

    public void cloneRepositories(final List<Repository> repositories, final Consumer<String> statusCallback) {
        final File kudeSubmissionsFolder = new File(KUDE_SUBMISSIONS_PATH);
        if (!kudeSubmissionsFolder.exists() && !kudeSubmissionsFolder.mkdirs()) {
            log.error("Failed to create KUDE submissions folder.");
            throw new RuntimeException("Failed to create KUDE submissions folder. Cannot continue.");
        }

        int count = 0;
        for (final Repository repository : repositories) {
            try (Git git = Git.cloneRepository()
                    .setURI(repository.getRepositoryUrlWithCredentials())
                    .setDirectory(new File(KUDE_SUBMISSIONS_PATH + repository.name()))
                    .call()) {
            } catch (final InvalidRemoteException e) {
                log.error("Could not clone repository {}. The error was: {}", repository.url(), e.getMessage());
            } catch (final Exception e) {
                log.error("Could not clone repository {}. The error was: {}", repository.url(), e.getMessage());
                throw new RuntimeException(e);
            }
            count++;

            log.debug("Cloned repository {} to {}", repository.url(), KUDE_SUBMISSIONS_PATH + repository.name());
            log.debug("{} of {} repositories cloned", count, repositories.size());

            if (count % 10 == 0) {
                statusCallback.accept("Cloned " + count + " of " + repositories.size() + " repositories.");
            }
        }
    }

    public void deleteAllRepositories() {
        final File kudeSubmissionsFolder = new File(KUDE_SUBMISSIONS_PATH);
        if (!kudeSubmissionsFolder.exists()) {
            log.error("KUDE submissions folder does not exist.");
            throw new RuntimeException("KUDE submissions folder does not exist.");
        }
        try {
            FileUtils.deleteDirectory(new File(KUDE_SUBMISSIONS_PATH));
        } catch (IOException e) {
            log.error("Could not delete KUDE submissions folder. The error was: {}", e.getMessage());
            throw new RuntimeException("Could not delete KUDE submissions folder. The error was: " + e.getMessage());
        }
    }

    public void deletePlagiarismResultIfExists() {
        final File plagiarismResult = new File(KUDE_PLAGIARISM_RESULTS_PATH);
        if (!plagiarismResult.exists()) {
            return;
        }

        if (!plagiarismResult.delete()) {
            log.error("Could not delete plagiarism result.");
            throw new RuntimeException("Could not delete plagiarism result.");
        }
    }
}
