package com.github.kudeplatform.evaluationengine;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Push
@SpringBootApplication
@Theme("kude-theme")
public class EvaluationEngineApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(EvaluationEngineApplication.class);
    }

}
