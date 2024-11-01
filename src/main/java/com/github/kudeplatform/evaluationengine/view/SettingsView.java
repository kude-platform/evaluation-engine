package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.RegexpValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;

/**
 * @author timo.buechert
 */
@Route(value = "/app/settings", layout = AppView.class)
public class SettingsView extends VerticalLayout {

    final SettingsService settingsService;

    final TextField timeoutInSeconds;

    final TextField replicationFactor;

    final Button saveButton;

    String timeoutInSecondsValue = "";

    String replicationFactorValue = "";

    @Autowired
    public SettingsView(final SettingsService settingsService) {
        this.settingsService = settingsService;
        final H2 title = new H2("App Settings");
        this.add(title);

        this.timeoutInSeconds = new TextField("Timeout in seconds");
        this.replicationFactor = new TextField("Replication factor");
        this.saveButton = new Button("Save");

        final FormLayout formLayout = new FormLayout();
        formLayout.add(timeoutInSeconds);
        formLayout.add(replicationFactor);
        formLayout.add(saveButton);
        this.add(formLayout);
    }

    @PostConstruct
    public void init() {
        final Binder<String> timeoutInSecondsBinder = new Binder<>(String.class);
        timeoutInSecondsBinder.forField(timeoutInSeconds)
                .withValidator(new RegexpValidator("Only numbers are allowed", "^[0-9]*$"))
                .withValidator(new StringLengthValidator("Must be between 1 and 5 characters", 1, 5))
                .bind(this::getTimeoutInSecondsValue, this::setTimeoutInSecondsValue);

        this.timeoutInSeconds.setValue(String.valueOf(settingsService.getTimeoutInSeconds()));

        final Binder<String> replicationFactorBinder = new Binder<>(String.class);
        replicationFactorBinder.forField(replicationFactor)
                .withValidator(new RegexpValidator("Only numbers are allowed", "^[0-9]*$"))
                .bind(this::getReplicationFactorValue, this::setReplicationFactorValue);

        this.replicationFactor.setValue(String.valueOf(settingsService.getReplicationFactor()));

        this.saveButton.addClickListener(event -> {
            if (timeoutInSecondsBinder.validate().isOk()
                    && replicationFactorBinder.validate().isOk()) {
                timeoutInSecondsBinder.writeBeanIfValid(timeoutInSecondsValue);
                replicationFactorBinder.writeBeanIfValid(replicationFactorValue);

                settingsService.setTimeoutInSeconds(timeoutInSecondsValue);
                settingsService.setReplicationFactor(replicationFactorValue);

                Notification.show("Saved settings", 5000, Notification.Position.TOP_CENTER);
            }

        });
    }

    private void setTimeoutInSecondsValue(String bean, String fieldValue) {
        this.timeoutInSecondsValue = fieldValue;
    }

    private String getTimeoutInSecondsValue(String bean) {
        return this.timeoutInSecondsValue;
    }

    private void setReplicationFactorValue(String bean, String fieldValue) {
        this.replicationFactorValue = fieldValue;
    }

    private String getReplicationFactorValue(String bean) {
        return this.replicationFactorValue;
    }

}
