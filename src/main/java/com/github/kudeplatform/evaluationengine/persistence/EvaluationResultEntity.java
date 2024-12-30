package com.github.kudeplatform.evaluationengine.persistence;

import com.github.kudeplatform.evaluationengine.domain.EvaluationStatus;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * @author timo.buechert
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationResultEntity {

    @Id
    private String taskId;
    
    private String name;

    private ZonedDateTime timestamp;

    private EvaluationStatus status;
    
    private boolean logsAvailable;

    private boolean resultsAvailable;

    private boolean resultsCorrect;

    @ElementCollection
    private List<Integer> podIndicesReadyToRun;

    @Lob
    private String message;

}
