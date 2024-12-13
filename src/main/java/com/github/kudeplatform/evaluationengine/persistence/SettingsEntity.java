package com.github.kudeplatform.evaluationengine.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
public class SettingsEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String settingsKey;

    @Lob
    private String settingsValue;

}
