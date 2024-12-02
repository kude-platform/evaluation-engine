package com.github.kudeplatform.evaluationengine.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author timo.buechert
 */
@Data
@Builder
public class ErrorEventDefinition {

    private String category;

    private List<String> errorPatterns;

    private boolean fatal;

}
