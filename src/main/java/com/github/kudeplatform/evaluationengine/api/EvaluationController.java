package com.github.kudeplatform.evaluationengine.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author timo.buechert
 */
@RestController
@RequestMapping("/api/evaluation")
@Slf4j
public class EvaluationController {

    @RequestMapping(value = "/jobFinished/{evaluation_id}", method = RequestMethod.GET)
    public void evaluationFinished(
            @PathVariable("evaluation_id") String evaluationId,
            @RequestParam("node_id") String nodeId,
            HttpServletResponse response) {

    }

}
