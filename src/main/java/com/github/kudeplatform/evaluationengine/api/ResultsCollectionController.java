package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * @author timo.buechert
 */
@RestController
@Slf4j
public class ResultsCollectionController {

    private final EvaluationResultRepository evaluationResultRepository;

    @Autowired
    public ResultsCollectionController(final EvaluationResultRepository evaluationResultRepository) {
        this.evaluationResultRepository = evaluationResultRepository;
    }

    @RequestMapping(value = "/results/{jobId}", method = RequestMethod.POST)
    public void saveResults(@PathVariable String jobId, @RequestParam("file") MultipartFile file) {
        final String fileName = "results-" + jobId;
        final File resultsFile = new File(System.getProperty("java.io.tmpdir") + fileName + ".txt");

        try {
            file.transferTo(resultsFile);
        } catch (Exception e) {
            log.error("Failed to save results file.", e);
            throw new RuntimeException("Failed to save results file.");
        }

        this.evaluationResultRepository.findById(jobId)
                .ifPresent(evaluationResultEntity -> {
                    evaluationResultEntity.setResultsAvailable(true);
                    this.evaluationResultRepository.save(evaluationResultEntity);
                });
    }

}
