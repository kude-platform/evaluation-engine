package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author timo.buechert
 */
@RestController
@RequestMapping("/api/evaluation")
@Slf4j
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(final EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @RequestMapping(value = "/allPodsReadyToRun/{evaluation_id}", method = RequestMethod.GET)
    public String areAllPodsReadyToRun(@PathVariable("evaluation_id") String evaluationId) {
        if (evaluationService.areAllPodsReadyToRun(evaluationId)) {
            return "READY";
        }

        return "NOT_READY";
    }

    @RequestMapping(value = "/anyEvaluationRunning", method = RequestMethod.GET)
    public boolean isAnyEvaluationRunning() {
        return !evaluationService.isNoJobRunning();
    }

}
