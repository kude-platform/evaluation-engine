package com.github.kudeplatform.evaluationengine.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author timo.buechert
 */
@Data
@Builder
public class LogEventDefinition {

    private String type;

    private List<String> patterns;

    private String level;

}
