package com.github.kudeplatform.evaluationengine.api;

import com.github.kudeplatform.evaluationengine.domain.EvaluationEvent;
import lombok.Data;

/**
 * @author timo.buechert
 */
@Data
public class Event {

    private String message;

    private String type;

    private String level;

    private String durationInSeconds;

    public boolean isFatal() {
        return EvaluationEvent.LEVEL_FATAL.equals(level);
    }

}
