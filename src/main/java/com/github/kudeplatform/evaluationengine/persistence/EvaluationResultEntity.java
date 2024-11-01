package com.github.kudeplatform.evaluationengine.persistence;

import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * @author timo.buechert
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationResultEntity {

    @Id
    private UUID taskId;

    private ZonedDateTime timestamp;

    private EvaluationStatus status;
    
    private boolean logsAvailable;

    @Lob
    private String message;

}
