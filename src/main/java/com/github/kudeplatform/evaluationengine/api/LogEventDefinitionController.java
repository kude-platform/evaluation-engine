package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.LogEventDefinition;
import com.github.kudeplatform.evaluationengine.persistence.LogEventDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

/**
 * @author timo.buechert
 */
@RestController
@RequestMapping("/api/logEventDefinition")
@Slf4j
public class LogEventDefinitionController {

    private final LogEventDefinitionRepository logEventDefinitionRepository;

    public LogEventDefinitionController(final LogEventDefinitionRepository logEventDefinitionRepository) {
        this.logEventDefinitionRepository = logEventDefinitionRepository;
    }

    @RequestMapping(method = RequestMethod.GET)
    public List<LogEventDefinition> getLogEventDefinitions() {
        return this.logEventDefinitionRepository.findAll().stream()
                .map(entity -> LogEventDefinition.builder()
                        .type(entity.getType())
                        .patterns(Stream.of(entity.getPatterns().split(",")).map(String::trim).toList())
                        .level(entity.getLevel())
                        .build())
                .toList();
    }
}
