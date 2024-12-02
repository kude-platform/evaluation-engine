package com.github.kudeplatform.evaluationengine.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author timo.buechert
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorEventDefinitionEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String category;

    private String errorPatterns;

    private boolean fatal;

}
