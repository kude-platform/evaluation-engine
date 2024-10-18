package com.github.kudeplatform.evaluationengine;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Push
@SpringBootApplication
public class EvaluationEngineApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(EvaluationEngineApplication.class);
    }

}
