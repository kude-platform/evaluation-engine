package com.github.kudeplatform.evaluationengine.persistence;

import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * @author timo.buechert
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationEventEntity {

    @Id
    @GeneratedValue
    private Long id;

    private ZonedDateTime timestamp;

    private String taskId;

    private String index;

    private EvaluationStatus status;

    @Lob
    private String message;

    private String type;

    private String level;
    
}
