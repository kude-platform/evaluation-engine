package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.persistence.ErrorEventDefinitionEntity;
import com.github.kudeplatform.evaluationengine.persistence.ErrorEventDefinitionRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.editor.Editor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
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

    final ErrorEventDefinitionRepository errorEventDefinitionRepository;

    final TextField timeoutInSeconds;

    final TextField replicationFactor;

    final TextField maxJobsPerNode;

    final TextField gitUsername;

    final PasswordField gitToken;

    final TextField evaluationImage;

    final TextField cpuRequest;

    final TextField cpuLimit;

    final TextField memoryRequest;

    final TextField memoryLimit;

    final Button saveButton;


    final TextArea expectedSolutionInput = new TextArea("Expected Solution");

    final Button saveExpectedSolutionButton = new Button("Save");


    final Grid<ErrorEventDefinitionEntity> grid;

    String timeoutInSecondsValue = "";

    String replicationFactorValue = "";

    String maxJobsPerNodeValue = "";

    String gitUsernameValue = "";

    String gitTokenValue = "";

    String evaluationImageValue = "";

    String cpuRequestValue = "";

    String cpuLimitValue = "";

    String memoryRequestValue = "";

    String memoryLimitValue = "";


    List<Binder> binders = new ArrayList<>();

    @Autowired
    public SettingsView(final SettingsService settingsService, final EvaluationService evaluationService,
                        final ErrorEventDefinitionRepository errorEventDefinitionRepository) {
        this.settingsService = settingsService;
        this.evaluationService = evaluationService;
        this.errorEventDefinitionRepository = errorEventDefinitionRepository;
        final H2 title = new H2("App Settings");
        this.add(title);

        this.timeoutInSeconds = new TextField("Timeout in seconds");
        this.replicationFactor = new TextField("Replication factor");
        this.maxJobsPerNode = new TextField("Max jobs per node");
        this.gitUsername = new TextField("Git username");
        this.gitToken = new PasswordField("Git token");
        this.evaluationImage = new TextField("Evaluation image");
        this.cpuRequest = new TextField("CPU request");
        this.cpuLimit = new TextField("CPU limit");
        this.memoryRequest = new TextField("Memory request");
        this.memoryLimit = new TextField("Memory limit");
        this.saveButton = new Button("Save");

        final FormLayout formLayout = new FormLayout();
        formLayout.add(timeoutInSeconds);
        formLayout.add(replicationFactor);
        formLayout.add(maxJobsPerNode);
        formLayout.add(gitUsername);
        formLayout.add(gitToken);
        formLayout.add(evaluationImage);
        formLayout.add(cpuRequest);
        formLayout.add(cpuLimit);
        formLayout.add(memoryRequest);
        formLayout.add(memoryLimit);
        formLayout.add(saveButton);
        this.add(formLayout);

        this.add(new Hr());

        final H3 expectedSolutionTitle = new H3("Expected Solution");
        this.add(expectedSolutionTitle);
        this.expectedSolutionInput.setSizeFull();
        this.expectedSolutionInput.setValue(settingsService.getExpectedSolution());
        this.add(expectedSolutionInput);
        this.add(saveExpectedSolutionButton);
        this.saveExpectedSolutionButton.addClickListener(event -> {
            settingsService.setExpectedSolution(expectedSolutionInput.getValue());
            Notification.show("Saved expected solution", 5000, Notification.Position.TOP_CENTER);
        });


        this.add(new Hr());

        final H3 errorEventDefinitionsTitle = new H3("Error Event Definitions");
        this.add(errorEventDefinitionsTitle);

        final Span errorEventDefinitionsDescription = new Span("Define error event definitions to categorize error events and mark them as fatal" +
                "or non-fatal. Error patterns are comma-separated strings that are used to match error messages. The error will be updated in the " +
                "log-analyzer edge-nodes periodically.");

        this.add(errorEventDefinitionsDescription);

        // Dialog to add new error event definitions
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New Error Event Definition");
        TextField modalCategoryField = new TextField("Category");
        TextField modalErrorPatternsField = new TextField("Error patterns");
        Checkbox modalFatalCheckbox = new Checkbox("Fatal");

        VerticalLayout dialogLayout = new VerticalLayout(modalCategoryField,
                modalErrorPatternsField, modalFatalCheckbox);
        dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        dialogLayout.getStyle().set("width", "18rem").set("max-width", "100%");
        dialog.add(dialogLayout);

        Button modalSaveButton = new Button("Add", e -> {
            ErrorEventDefinitionEntity errorEventDefinitionEntity = new ErrorEventDefinitionEntity();
            errorEventDefinitionEntity.setCategory(modalCategoryField.getValue());
            errorEventDefinitionEntity.setErrorPatterns(modalErrorPatternsField.getValue());
            errorEventDefinitionEntity.setFatal(modalFatalCheckbox.getValue());
            this.errorEventDefinitionRepository.save(errorEventDefinitionEntity);
            this.updateGrid();
            dialog.close();
        });
        modalSaveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button modalCancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(modalCancelButton);
        dialog.getFooter().add(modalSaveButton);
        Button modalShowModalButton = new Button("New item", e -> dialog.open());
        add(dialog, modalShowModalButton);

        // Grid to display error event definitions
        ValidationMessage categoryValidationMessage = new ValidationMessage();
        ValidationMessage errorPatternsValidationMessage = new ValidationMessage();

        grid = new Grid<>(ErrorEventDefinitionEntity.class, false);
        final Editor<ErrorEventDefinitionEntity> editor = grid.getEditor();

        Grid.Column<ErrorEventDefinitionEntity> categoryColumn = grid
                .addColumn(ErrorEventDefinitionEntity::getCategory).setHeader("Category")
                .setWidth("40%").setFlexGrow(0);
        Grid.Column<ErrorEventDefinitionEntity> errorPatternsColumn = grid
                .addColumn(ErrorEventDefinitionEntity::getErrorPatterns)
                .setHeader("Error patterns").setWidth("40%").setFlexGrow(0);

        Grid.Column<ErrorEventDefinitionEntity> fatalColumn = grid
                .addComponentColumn(errorEventDefinitionEntity -> {
                    final Checkbox fatalCheckbox = new Checkbox();
                    fatalCheckbox.setValue(errorEventDefinitionEntity.isFatal());
                    fatalCheckbox.setEnabled(false);
                    return fatalCheckbox;
                }).setHeader("Fatal").setWidth("5%").setFlexGrow(0);

        Grid.Column<ErrorEventDefinitionEntity> editColumn = grid.addComponentColumn(ErrorEventDefinitionEntity -> {
            final HorizontalLayout actions = new HorizontalLayout();
            final Button deleteButton = new Button("Delete");
            deleteButton.addClickListener(e -> {
                this.errorEventDefinitionRepository.delete(ErrorEventDefinitionEntity);
                this.updateGrid();
            });
            actions.add(deleteButton);
            final Button editButton = new Button("Edit");
            editButton.addClickListener(e -> {
                if (editor.isOpen())
                    editor.cancel();
                grid.getEditor().editItem(ErrorEventDefinitionEntity);
            });
            actions.add(editButton);
            return actions;
        }).setWidth("15%").setFlexGrow(0);

        Binder<ErrorEventDefinitionEntity> binder = new Binder<>(ErrorEventDefinitionEntity.class);
        editor.setBinder(binder);
        editor.setBuffered(true);

        TextField categoryField = new TextField();
        categoryField.setWidthFull();
        binder.forField(categoryField)
                .asRequired("Category must not be empty")
                .withStatusLabel(categoryValidationMessage)
                .bind(ErrorEventDefinitionEntity::getCategory, ErrorEventDefinitionEntity::setCategory);
        categoryColumn.setEditorComponent(categoryField);

        TextField errorPatternsField = new TextField();
        errorPatternsField.setWidthFull();
        binder.forField(errorPatternsField).asRequired("Error patterns must not be empty")
                .withStatusLabel(errorPatternsValidationMessage)
                .bind(ErrorEventDefinitionEntity::getErrorPatterns, ErrorEventDefinitionEntity::setErrorPatterns);
        errorPatternsColumn.setEditorComponent(errorPatternsField);

        Checkbox fatalCheckbox = new Checkbox();
        fatalCheckbox.setWidthFull();
        binder.forField(fatalCheckbox)
                .bind(ErrorEventDefinitionEntity::isFatal, ErrorEventDefinitionEntity::setFatal);
        fatalColumn.setEditorComponent(fatalCheckbox);

        Button saveButton = new Button("Save", e -> editor.save());
        Button cancelButton = new Button(VaadinIcon.CLOSE.create(),
                e -> editor.cancel());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ICON,
                ButtonVariant.LUMO_ERROR);
        HorizontalLayout actions = new HorizontalLayout(saveButton,
                cancelButton);
        actions.setPadding(false);
        editColumn.setEditorComponent(actions);

        editor.addSaveListener(e -> {
            this.errorEventDefinitionRepository.save(e.getItem());
            grid.setItems(this.errorEventDefinitionRepository.findAll());
        });

        this.add(grid, categoryValidationMessage, errorPatternsValidationMessage);
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

        final Binder<String> maxJobsPerNodeBinder = new Binder<>(String.class);
        maxJobsPerNodeBinder.forField(maxJobsPerNode)
                .withValidator(new RegexpValidator("Only numbers are allowed", "^[0-9]*$"))
                .bind(this::getMaxJobsPerNodeValue, this::setMaxJobsPerNodeValue);
        binders.add(maxJobsPerNodeBinder);

        this.maxJobsPerNode.setValue(String.valueOf(settingsService.getMaxJobsPerNode()));

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

        final Binder<String> evaluationImageBinder = new Binder<>(String.class);
        evaluationImageBinder.forField(evaluationImage)
                .bind(this::getEvaluationImageValue, this::setEvaluationImageValue);
        binders.add(evaluationImageBinder);

        this.evaluationImage.setValue(settingsService.getEvaluationImage());

        final Binder<String> cpuRequestBinder = new Binder<>(String.class);
        cpuRequestBinder.forField(cpuRequest)
                .bind(this::getCpuRequestValue, this::setCpuRequestValue);
        binders.add(cpuRequestBinder);

        this.cpuRequest.setValue(settingsService.getCpuRequest());

        final Binder<String> cpuLimitBinder = new Binder<>(String.class);
        cpuLimitBinder.forField(cpuLimit)
                .bind(this::getCpuLimitValue, this::setCpuLimitValue);
        binders.add(cpuLimitBinder);

        this.cpuLimit.setValue(settingsService.getCpuLimit());

        final Binder<String> memoryRequestBinder = new Binder<>(String.class);
        memoryRequestBinder.forField(memoryRequest)
                .bind(this::getMemoryRequestValue, this::setMemoryRequestValue);
        binders.add(memoryRequestBinder);

        this.memoryRequest.setValue(settingsService.getMemoryRequest());

        final Binder<String> memoryLimitBinder = new Binder<>(String.class);
        memoryLimitBinder.forField(memoryLimit)
                .bind(this::getMemoryLimitValue, this::setMemoryLimitValue);
        binders.add(memoryLimitBinder);

        this.memoryLimit.setValue(settingsService.getMemoryLimit());

        this.saveButton.addClickListener(event -> {
            if (this.areBindersValid()) {
                timeoutInSecondsBinder.writeBeanIfValid(timeoutInSecondsValue);
                replicationFactorBinder.writeBeanIfValid(replicationFactorValue);
                maxJobsPerNodeBinder.writeBeanIfValid(maxJobsPerNodeValue);
                gitUsernameBinder.writeBeanIfValid(gitUsernameValue);
                gitTokenBinder.writeBeanIfValid(gitTokenValue);
                evaluationImageBinder.writeBeanIfValid(evaluationImageValue);
                cpuRequestBinder.writeBeanIfValid(cpuRequestValue);
                cpuLimitBinder.writeBeanIfValid(cpuLimitValue);
                memoryRequestBinder.writeBeanIfValid(memoryRequestValue);
                memoryLimitBinder.writeBeanIfValid(memoryLimitValue);

                settingsService.setTimeoutInSeconds(timeoutInSecondsValue);
                try {
                    evaluationService.updateNumberOfParallelJobs(Integer.parseInt(replicationFactorValue), Integer.parseInt(maxJobsPerNodeValue));
                    settingsService.setReplicationFactor(replicationFactorValue);
                    settingsService.setMaxJobsPerNode(maxJobsPerNodeValue);
                } catch (IllegalStateException e) {
                    Notification.show("Could not update replication factor because evaluations are currently active", 5000,
                            Notification.Position.MIDDLE);
                    this.replicationFactor.setValue(String.valueOf(settingsService.getReplicationFactor()));
                    this.maxJobsPerNode.setValue(String.valueOf(settingsService.getMaxJobsPerNode()));
                }
                settingsService.setGitUsername(gitUsernameValue);
                settingsService.setGitToken(gitTokenValue);
                settingsService.setEvaluationImage(evaluationImageValue);
                settingsService.setCpuRequest(cpuRequestValue);
                settingsService.setCpuLimit(cpuLimitValue);
                settingsService.setMemoryRequest(memoryRequestValue);
                settingsService.setMemoryLimit(memoryLimitValue);

                Notification.show("Saved settings", 5000, Notification.Position.TOP_CENTER);
            }

        });
        this.saveButton.addClickShortcut(Key.ENTER);

        this.addInitialErrorEventDefinitions();
        this.updateGrid();
    }

    private void updateGrid() {
        grid.setItems(this.errorEventDefinitionRepository.findAll());
    }

    private void addInitialErrorEventDefinitions() {
        final List<ErrorEventDefinitionEntity> initialErrorEventDefinitions = List.of(

                new ErrorEventDefinitionEntity(null, "NULL_POINTER_EXCEPTION", "NullPointerException,NPE", false),
                new ErrorEventDefinitionEntity(null, "ARRAY_INDEX_OUT_OF_BOUNDS_EXCEPTION", "ArrayIndexOutOfBoundsException,ArrayIndexOutOfBounds", false),
                new ErrorEventDefinitionEntity(null, "CLASS_CAST_EXCEPTION", "ClassCastException, ClassCast", false),
                new ErrorEventDefinitionEntity(null, "CONNECTION_PROBLEM", "ConnectException, StreamTcpException, Couldn't join seed nodes", false),
                new ErrorEventDefinitionEntity(null, "OUT_OF_MEMORY", "OutOfMemoryError", true),
                new ErrorEventDefinitionEntity(null, "MISSING_HANDLE", "dead letters encountered", false),
                new ErrorEventDefinitionEntity(null, "Message too large", "Failed to serialize oversized message", false)
        );

        final List<ErrorEventDefinitionEntity> errorEventDefinitions = this.errorEventDefinitionRepository.findAll();
        if (errorEventDefinitions.isEmpty()) {
            this.errorEventDefinitionRepository.saveAll(initialErrorEventDefinitions);
        }
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

    private void setMaxJobsPerNodeValue(String bean, String fieldValue) {
        this.maxJobsPerNodeValue = fieldValue;
    }

    private String getMaxJobsPerNodeValue(String bean) {
        return this.maxJobsPerNodeValue;
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

    private void setEvaluationImageValue(String bean, String fieldValue) {
        this.evaluationImageValue = fieldValue;
    }

    private String getEvaluationImageValue(String bean) {
        return this.evaluationImageValue;
    }

    private void setCpuRequestValue(String bean, String fieldValue) {
        this.cpuRequestValue = fieldValue;
    }

    private String getCpuRequestValue(String bean) {
        return this.cpuRequestValue;
    }

    private void setCpuLimitValue(String bean, String fieldValue) {
        this.cpuLimitValue = fieldValue;
    }

    private String getCpuLimitValue(String bean) {
        return this.cpuLimitValue;
    }

    private void setMemoryRequestValue(String bean, String fieldValue) {
        this.memoryRequestValue = fieldValue;
    }

    private String getMemoryRequestValue(String bean) {
        return this.memoryRequestValue;
    }

    private void setMemoryLimitValue(String bean, String fieldValue) {
        this.memoryLimitValue = fieldValue;
    }

    private String getMemoryLimitValue(String bean) {
        return this.memoryLimitValue;
    }

}
