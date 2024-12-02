package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.ErrorEventDefinition;
import com.github.kudeplatform.evaluationengine.persistence.ErrorEventDefinitionRepository;
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
@RequestMapping("/api/errorEventDefinition")
@Slf4j
public class ErrorEventDefinitionController {

    private final ErrorEventDefinitionRepository errorEventDefinitionRepository;

    public ErrorEventDefinitionController(final ErrorEventDefinitionRepository errorEventDefinitionRepository) {
        this.errorEventDefinitionRepository = errorEventDefinitionRepository;
    }

    @RequestMapping(method = RequestMethod.GET)
    public List<ErrorEventDefinition> getErrorEventDefinitions() {
        return this.errorEventDefinitionRepository.findAll().stream()
                .map(entity -> ErrorEventDefinition.builder()
                        .category(entity.getCategory())
                        .errorPatterns(Stream.of(entity.getErrorPatterns().split(",")).map(String::trim).toList())
                        .fatal(entity.isFatal())
                        .build())
                .toList();
    }
}
