package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.RegexpValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * @author timo.buechert
 */
@Route(value = "/app/settings", layout = AppView.class)
public class SettingsView extends VerticalLayout {

    final SettingsService settingsService;

    final EvaluationService evaluationService;

    final TextField timeoutInSeconds;

    final TextField replicationFactor;

    final TextField gitUsername;

    final PasswordField gitToken;

    final Button saveButton;

    String timeoutInSecondsValue = "";

    String replicationFactorValue = "";

    String gitUsernameValue = "";

    String gitTokenValue = "";

    List<Binder> binders = new ArrayList<>();

    @Autowired
    public SettingsView(final SettingsService settingsService, final EvaluationService evaluationService) {
        this.settingsService = settingsService;
        this.evaluationService = evaluationService;
        final H2 title = new H2("App Settings");
        this.add(title);

        this.timeoutInSeconds = new TextField("Timeout in seconds");
        this.replicationFactor = new TextField("Replication factor");
        this.gitUsername = new TextField("Git username");
        this.gitToken = new PasswordField("Git token");
        this.saveButton = new Button("Save");

        final FormLayout formLayout = new FormLayout();
        formLayout.add(timeoutInSeconds);
        formLayout.add(replicationFactor);
        formLayout.add(gitUsername);
        formLayout.add(gitToken);
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
        binders.add(timeoutInSecondsBinder);

        this.timeoutInSeconds.setValue(String.valueOf(settingsService.getTimeoutInSeconds()));

        final Binder<String> replicationFactorBinder = new Binder<>(String.class);
        replicationFactorBinder.forField(replicationFactor)
                .withValidator(new RegexpValidator("Only numbers are allowed", "^[0-9]*$"))
                .bind(this::getReplicationFactorValue, this::setReplicationFactorValue);
        binders.add(replicationFactorBinder);

        this.replicationFactor.setValue(String.valueOf(settingsService.getReplicationFactor()));

        final Binder<String> gitUsernameBinder = new Binder<>(String.class);
        gitUsernameBinder.forField(gitUsername)
                .bind(this::getGitUsernameValue, this::setGitUsernameValue);
        binders.add(gitUsernameBinder);

        this.gitUsername.setValue(settingsService.getGitUsername());

        final Binder<String> gitTokenBinder = new Binder<>(String.class);
        gitTokenBinder.forField(gitToken)
                .bind(this::getGitTokenValue, this::setGitTokenValue);
        binders.add(gitTokenBinder);

        this.gitToken.setValue(settingsService.getGitToken());

        this.saveButton.addClickListener(event -> {
            if (this.areBindersValid()) {
                timeoutInSecondsBinder.writeBeanIfValid(timeoutInSecondsValue);
                replicationFactorBinder.writeBeanIfValid(replicationFactorValue);
                gitUsernameBinder.writeBeanIfValid(gitUsernameValue);
                gitTokenBinder.writeBeanIfValid(gitTokenValue);

                settingsService.setTimeoutInSeconds(timeoutInSecondsValue);
                try {
                    evaluationService.updateNumberOfParallelJobs(Integer.parseInt(replicationFactorValue));
                    settingsService.setReplicationFactor(replicationFactorValue);
                } catch (IllegalStateException e) {
                    Notification.show("Could not update replication factor because evaluations are currently active", 5000,
                            Notification.Position.MIDDLE);
                    this.replicationFactor.setValue(String.valueOf(settingsService.getReplicationFactor()));
                }
                settingsService.setGitUsername(gitUsernameValue);
                settingsService.setGitToken(gitTokenValue);

                Notification.show("Saved settings", 5000, Notification.Position.TOP_CENTER);
            }

        });
        this.saveButton.addClickShortcut(Key.ENTER);
    }

    private boolean areBindersValid() {
        return this.binders.stream().allMatch(binder -> binder.validate().isOk());
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

    private void setGitUsernameValue(String bean, String fieldValue) {
        this.gitUsernameValue = fieldValue;
    }

    private String getGitUsernameValue(String bean) {
        return this.gitUsernameValue;
    }

    private void setGitTokenValue(String bean, String fieldValue) {
        this.gitTokenValue = fieldValue;
    }

    private String getGitTokenValue(String bean) {
        return this.gitTokenValue;
    }

}
