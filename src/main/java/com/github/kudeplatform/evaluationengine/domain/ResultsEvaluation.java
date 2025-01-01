package com.github.kudeplatform.evaluationengine.domain;

/**
 * @author timo.buechert
 */
public record ResultsEvaluation(int totalActual, int correctActual, int correctExpected, boolean correct,
                                String resultProportion) {
}
