package com.github.kudeplatform.evaluationengine.view;

/**
 * @author timo.buechert
 */
public interface NotifiableComponent {

    void dataChanged();

    void dataChanged(String taskId);
    
}
