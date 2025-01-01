package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.ResultsEvaluation;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

import static com.github.kudeplatform.evaluationengine.service.FileSystemService.KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR;

/**
 * @author timo.buechert
 */
@RestController
@RequestMapping("/api/fileIngestion")
@Slf4j
public class FileIngestionController {

    private final EvaluationResultRepository evaluationResultRepository;

    private final EvaluationService evaluationService;

    @Autowired
    public FileIngestionController(final EvaluationResultRepository evaluationResultRepository,
                                   final EvaluationService evaluationService) {
        this.evaluationResultRepository = evaluationResultRepository;
        this.evaluationService = evaluationService;
    }

    @RequestMapping(value = "/results/{jobId}", method = RequestMethod.POST)
    public void saveResults(@PathVariable String jobId, @RequestParam("file") MultipartFile file) throws IOException {
        final String results = new String(file.getBytes());

        final String fileName = "results-" + jobId;
        final File resultsFile = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR + fileName + ".txt");

        try {
            file.transferTo(resultsFile);
        } catch (Exception e) {
            log.error("Failed to save results file.", e);
            throw new RuntimeException("Failed to save results file.");
        }

        final ResultsEvaluation resultsEvaluation = this.evaluationService.areResultsCorrect(results);

        this.evaluationResultRepository.findById(jobId)
                .ifPresent(evaluationResultEntity -> {
                    evaluationResultEntity.setResultsAvailable(true);
                    evaluationResultEntity.setResultsCorrect(resultsEvaluation.correct());
                    evaluationResultEntity.setResultProportion(resultsEvaluation.resultProportion());
                    this.evaluationResultRepository.save(evaluationResultEntity);
                    this.evaluationService.notifyView();
                });
    }

    @RequestMapping(value = "/logs/{jobId}/{instanceId}", method = RequestMethod.POST)
    public void saveLogs(@PathVariable String jobId, @PathVariable String instanceId, @RequestParam("file") MultipartFile file) {
        final String fileName = "logs-" + jobId + "-" + instanceId + file.getOriginalFilename();
        final File resultsFile = new File(KUDE_TMP_FOLDER_PATH_WITH_TRAILING_SEPARATOR + fileName);

        try {
            file.transferTo(resultsFile);
        } catch (Exception e) {
            log.error("Failed to save logs file.", e);
            throw new RuntimeException("Failed to save logs file.");
        }

        this.evaluationResultRepository.findById(jobId)
                .ifPresent(evaluationResultEntity -> {
                    evaluationResultEntity.setLogsAvailable(true);
                    this.evaluationResultRepository.save(evaluationResultEntity);
                    this.evaluationService.notifyView();
                });
    }

}
