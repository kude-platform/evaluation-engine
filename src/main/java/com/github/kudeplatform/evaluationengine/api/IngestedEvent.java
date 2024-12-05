package com.github.kudeplatform.evaluationengine.api;

import lombok.Data;

import java.util.List;

/**
 * @author timo.buechert
 */
@Data
public class IngestedEvent {
    
    private String evaluationId;

    private String index;

    private List<String> errors;
    
    private List<Error> errorObjects;

}
