package com.github.kudeplatform.evaluationengine.api;

import lombok.Data;

/**
 * @author timo.buechert
 */
@Data
public class Error {

    private String message;
    
    private String category;

    private boolean fatal;

}
