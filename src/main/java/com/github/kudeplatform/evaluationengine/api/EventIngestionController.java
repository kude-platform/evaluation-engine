package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author timo.buechert
 */
@RestController
@Slf4j
public class EventIngestionController {

    private final EvaluationService evaluationService;

    public EventIngestionController(final EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @RequestMapping(value = "/ingest/event", method = RequestMethod.POST)
    public void ingestEvent(@RequestBody IngestedEvent ingestedEvent) {
        this.evaluationService.saveIngestedEvent(ingestedEvent);
    }

}
