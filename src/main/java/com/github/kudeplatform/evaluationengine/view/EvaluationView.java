package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.domain.Dataset;
import com.github.kudeplatform.evaluationengine.domain.GitEvaluationTask;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import com.github.kudeplatform.evaluationengine.service.FileSystemService;
import com.github.kudeplatform.evaluationengine.service.SettingsService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.validator.RegexpValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author timo.buechert
 */
@Route(value = "/app/evaluation", layout = AppView.class)
@Slf4j
@JsModule("./copytoclipboard.js")
public class EvaluationView extends VerticalLayout implements NotifiableComponent {

    private final EvaluationResultRepository evaluationResultRepository;

    private final EvaluationService evaluationService;

    private final SettingsService settingsService;

    private final List<NotifiableComponent> activeEvaluationViewComponents;

    private final TextField gitRepositoryUrl = new TextField("GIT Repository URL");

    private final TextField gitBranch = new TextField("GIT Branch", "", "");

    private final TextField name = new TextField("Name");

    private final Select<String> datasetName = new Select<>();

    private final Grid<EvaluationResultEntity> grid;

    private final List<String> javascriptTimeouts = new ArrayList<>();
    private final TextArea massUploadTextArea = new TextArea();

    private List<String> instanceStartCommands = new ArrayList<>();

    @Autowired
    public EvaluationView(final EvaluationResultRepository evaluationResultRepository,
                          final EvaluationService evaluationService,
                          final FileSystemService fileSystemService,
                          final SettingsService settingsService,
                          @Qualifier(value = "activeEvaluationViewComponents") final List<NotifiableComponent> activeEvaluationViewComponents) {
        this.evaluationResultRepository = evaluationResultRepository;
        this.evaluationService = evaluationService;
        this.settingsService = settingsService;
        this.activeEvaluationViewComponents = activeEvaluationViewComponents;

        this.datasetName.setLabel("Dataset");
        this.datasetName.setItems(fileSystemService.getAvailableDatasets().stream().map(Dataset::name).toList());
        this.datasetName.setValue(fileSystemService.getAvailableDatasets().stream().findFirst().map(Dataset::name).orElse(""));

        final H2 title = new H2("Evaluation");
        this.add(title);

        final VerticalLayout verticalLayout = new VerticalLayout();
        this.add(verticalLayout);

        final Span explanationSpan
                = new Span("Please submit a GIT repository URL to start the evaluation.");
        verticalLayout.add(explanationSpan);

        final HorizontalLayout horizontalLayout = new HorizontalLayout();

        horizontalLayout.add(gitRepositoryUrl);
        gitRepositoryUrl.setRequiredIndicatorVisible(true);
        gitRepositoryUrl.setErrorMessage("This field is required");
        gitRepositoryUrl.setTooltipText("In case the repository is private, please include an access token. " +
                "Example: https://token@github.com/username/repository");

        horizontalLayout.add(gitBranch);

        horizontalLayout.add(datasetName);
        datasetName.setRequiredIndicatorVisible(true);
        datasetName.setErrorMessage("This field is required");
        datasetName.setTooltipText("The dataset to use for the evaluation");

        horizontalLayout.add(name);
        name.setRequiredIndicatorVisible(true);
        name.setErrorMessage("This field is required");
        name.setTooltipText("Your name or a descriptive name for the evaluation task");

        final Binder<GitEvaluationTask> gitBinder = new Binder<>(GitEvaluationTask.class);
        gitBinder.forField(gitRepositoryUrl)
                .withValidator(new StringLengthValidator("GIT Repository URL must contain at least 1 character", 1, null))
                .withValidator(new RegexpValidator("GIT Repository URL must be in a URL format", "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"))
                .bind(GitEvaluationTask::repositoryUrl, GitEvaluationTask::setGitUrl);

        final Binder<GitEvaluationTask> gitBranchBinder = new Binder<>(GitEvaluationTask.class);
        gitBranchBinder.forField(gitBranch)
                .bind(GitEvaluationTask::gitBranch, GitEvaluationTask::setGitBranch);

        final Binder<GitEvaluationTask> nameBinder = new Binder<>(GitEvaluationTask.class);
        nameBinder.forField(name)
                .withValidator(new StringLengthValidator("Name must contain at least 1 character", 1, null))
                .bind(GitEvaluationTask::name, GitEvaluationTask::setName);

        final Button submitButton = createSubmitButton(gitBinder, nameBinder);
        horizontalLayout.add(submitButton);
        horizontalLayout.setAlignItems(Alignment.BASELINE);

        final Dialog massUploadDialog = new Dialog();
        massUploadDialog.setWidth("100%");
        massUploadDialog.setHeight("100%");

        final VerticalLayout dialogLayout = createMassUploadDialogLayout(massUploadDialog);
        massUploadDialog.add(dialogLayout);
        massUploadDialog.setHeaderTitle("Mass Upload");

        final Button closeButton = new Button(new Icon("lumo", "cross"),
                (e) -> massUploadDialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        massUploadDialog.getHeader().add(closeButton);

        final Button button = new Button("Mass Upload", e -> massUploadDialog.open());
        horizontalLayout.add(massUploadDialog, button);

        final Button cancelAllButton = new Button("Cancel All", e -> evaluationService.cancelAllEvaluationTasks());
        horizontalLayout.add(cancelAllButton);

        final Button deleteAllButton = new Button("Delete All", e -> evaluationService.deleteAllEvaluationTasks());
        horizontalLayout.add(deleteAllButton);

        final Button exportResults = new Button("Export Results", e -> {
            evaluationService.exportAllResultsToFile();
            final Span span = new Span("Exported results to file. Download the file here: ");
            final Anchor resultsDownloadAnchor = new Anchor();
            resultsDownloadAnchor.setText("File");
            resultsDownloadAnchor.setHref("/api/files/download/single/export.csv");
            resultsDownloadAnchor.getElement().setAttribute("download", true);

            final Notification notification = new Notification(span, resultsDownloadAnchor);
            notification.setDuration(5000);
            notification.setPosition(Notification.Position.TOP_CENTER);
            notification.open();
        });
        horizontalLayout.add(exportResults);

        verticalLayout.add(horizontalLayout);

        Span uploadSuccessSpan = new Span();
        verticalLayout.add(uploadSuccessSpan);

        this.grid = this.createEvaluationTable();
        verticalLayout.add(this.grid);
    }

    private Dialog createInstanceStartCommandsDialog(final boolean isMassUpload) {
        final Dialog dialog = new Dialog();
        dialog.setSizeFull();
        dialog.setHeaderTitle("Instance Start Commands");

        final Button closeButton = new Button(new Icon("lumo", "cross"),
                (e) -> dialog.close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(closeButton);

        final H2 title = new H2("Instance Start Commands");
        final Span explanationSpan = new Span("Please provide the start commands for each node.");

        dialog.add(title, explanationSpan, new Hr());

        final int replicationFactor = settingsService.getReplicationFactor();

        final VerticalLayout layout = new VerticalLayout();
        layout.setAlignItems(FlexComponent.Alignment.STRETCH);
        layout.setWidth("100%");

        final List<TextField> workerInstanceStartCommandFields = new ArrayList<>();
        final List<Binder<GitEvaluationTask>> binders = new ArrayList<>();

        for (int i = 0; i < replicationFactor; i++) {
            final HorizontalLayout horizontalLayout = new HorizontalLayout();
            horizontalLayout.setAlignItems(Alignment.BASELINE);

            final TextField textField = new TextField("Node " + i);
            textField.setRequiredIndicatorVisible(true);
            textField.setWidth("100%");
            textField.setErrorMessage("This field is required");
            textField.setPlaceholder("Start command for node " + i);
            textField.setClearButtonVisible(true);
            textField.setTooltipText("The command to start the evaluation instance on node " + i);
            textField.setValue(this.evaluationService.getTemplateStartCommand(i, this.datasetName.getValue()));
            horizontalLayout.add(textField);
            this.instanceStartCommands.add(textField.getValue());

            if (i != 0) {
                workerInstanceStartCommandFields.add(textField);
            }

            if (i == 1) {
                final Button copyButton = new Button("Copy to workers");
                copyButton.addClickListener(event -> workerInstanceStartCommandFields.stream().filter(field -> !field.equals(textField)).forEach(field -> field.setValue(textField.getValue())));
                horizontalLayout.add(copyButton);
            }

            layout.add(horizontalLayout);

            final Binder<GitEvaluationTask> binder = new Binder<>(GitEvaluationTask.class);
            int finalI = i;

            binder.forField(textField)
                    .withValidator(new StringLengthValidator("Start command must contain at least 1 character", 1, null))
                    .bind(gitEvaluationTask -> gitEvaluationTask.instanceStartCommands().get(finalI),
                            (gitEvaluationTask, s) -> gitEvaluationTask.instanceStartCommands().set(finalI, s));
            binders.add(binder);
        }

        dialog.add(layout);

        final Button submitButton = createSubmitButtonInInstanceStartCommandsDialog(binders, dialog, isMassUpload);
        submitButton.addClickShortcut(Key.ENTER);
        final Button cancelButton = new Button("Cancel", e -> dialog.close());

        dialog.getFooter().add(cancelButton);
        dialog.getFooter().add(submitButton);

        return dialog;
    }

    @NotNull
    private Button createSubmitButtonInInstanceStartCommandsDialog(List<Binder<GitEvaluationTask>> binders, Dialog dialog, boolean isMassUpload) {
        final Button submitButton = new Button("Submit");
        submitButton.addClickListener(event -> {
            final String uuid = UUID.randomUUID().toString();
            final GitEvaluationTask gitEvaluationTask = new GitEvaluationTask(gitRepositoryUrl.getValue(), uuid,
                    instanceStartCommands, name.getValue(), gitBranch.getValue(), datasetName.getValue());

            if (instanceStartCommands.stream().allMatch(StringUtils::hasText)
                    && binders.stream().allMatch(Binder::isValid)) {
                binders.forEach(binder -> binder.writeBeanIfValid(gitEvaluationTask));

                if (isMassUpload) {
                    this.evaluationService.submitMassEvaluationTask(this.massUploadTextArea.getValue(),
                            this.instanceStartCommands, this.gitBranch.getValue(), this.datasetName.getValue());
                } else {
                    this.evaluationService.submitEvaluationTask(gitEvaluationTask, true);
                    final Notification notification = Notification.show("Submitted. The Evaluation request will be handled " +
                                    "with the following ID: " + uuid + ".",
                            5000, Notification.Position.TOP_CENTER);
                    notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
                }
                dialog.close();

                this.instanceStartCommands = new ArrayList<>();
            }
        });
        return submitButton;
    }

    @NotNull
    private Button createSubmitButton(final Binder<GitEvaluationTask> gitBinder,
                                      final Binder<GitEvaluationTask> nameBinder) {
        final Button submitButton = new Button("Submit");
        submitButton.addClickShortcut(Key.ENTER);
        submitButton.addClickListener(event -> {
            if (gitBinder.validate().isOk() && nameBinder.validate().isOk() && !datasetName.isEmpty()) {
                this.createInstanceStartCommandsDialog(false).open();
            }

        });
        return submitButton;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.updateAll();
        synchronized (this.activeEvaluationViewComponents) {
            this.activeEvaluationViewComponents.add(this);
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        synchronized (this.activeEvaluationViewComponents) {
            this.activeEvaluationViewComponents.remove(this);
        }
        this.javascriptTimeouts.forEach(s -> UI.getCurrent().getPage().executeJs("clearInterval(" + s + ");console.error('cleared " + s + "');"));
    }

    private VerticalLayout createMassUploadDialogLayout(Dialog dialog) {
        massUploadTextArea.setPlaceholder("Paste the content of the mass upload file here. The content must be in CSV format following the pattern: " +
                "repositoryUrl;name");
        massUploadTextArea.setLabel("Mass Upload CSV Content");
        massUploadTextArea.setSizeFull();

        VerticalLayout fieldLayout = new VerticalLayout(massUploadTextArea);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.setSizeFull();

        final Button continueButton = new Button("Continue", e -> {
            if (massUploadTextArea.isEmpty()) {
                Notification.show("Please provide a valid CSV content", 5000, Notification.Position.MIDDLE);
            } else {
                dialog.close();
                this.createInstanceStartCommandsDialog(true).open();
            }
        });
        continueButton.addClickShortcut(Key.ENTER);
        final Button cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton);
        dialog.getFooter().add(continueButton);

        return fieldLayout;
    }

    private Grid<EvaluationResultEntity> createEvaluationTable() {
        final Grid<EvaluationResultEntity> grid = new Grid<>(EvaluationResultEntity.class, false);
        grid.addColumn(new ComponentRenderer<>(item -> {
            Anchor anchor = new Anchor();
            anchor.setText(item.getTaskId());
            anchor.setHref("/app/job/" + item.getTaskId());
            return anchor;
        })).setHeader("Task ID");

        grid.addColumn(EvaluationResultEntity::getName)
                .setTooltipGenerator(EvaluationResultEntity::getGitUrl)
                .setHeader("Name (Repository)").setSortable(true);

        grid.addColumn(evaluationResultEntity -> {
            final int positionInQueue = this.evaluationService.getPositionInQueue(evaluationResultEntity.getTaskId());
            return positionInQueue < 0 ? "" : positionInQueue;
        }).setHeader("Position In Queue");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isPending()) {
                return new Span("");
            }
            Anchor anchor = new Anchor();
            anchor.setText("Performance Graphs");
            anchor.setHref("/app/graphs/" + item.getTaskId());
            return anchor;
        })).setHeader("Performance Graphs");

        grid.addColumn(new ComponentRenderer<>(item -> {
            final Span status = new Span(item.getStatus().toString());
            switch (item.getStatus()) {
                case PENDING:
                case DEPLOYING:
                case RUNNING:
                    status.getElement().getThemeList().add("badge");
                    break;
                case SUCCEEDED:
                    status.getElement().getThemeList().add("badge success");
                    break;
                case TIMEOUT:
                case FAILED:
                    status.getElement().getThemeList().add("badge error");
                    break;
                case CANCELLED:
                    status.getElement().getThemeList().add("badge contrast");
                    break;
            }
            return status;
        })).setHeader("Status");

        grid.addColumn(EvaluationResultEntity::getMessage).setHeader("Message");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal() && item.getStartTimestamp() != null && item.getEndTimestamp() != null) {
                final Span span = new Span();
                final String grossDuration = String.valueOf(ChronoUnit.SECONDS.between(item.getStartTimestamp(), item.getEndTimestamp()));
                final String netDuration = item.getNetEvaluationDurationInSeconds();
                final String text = String.format("%s (net %s)", grossDuration, netDuration);
                span.setText(text);
                return span;
            }
            if (!item.getStatus().isRunning()) {
                return new Span("");
            }
            final Span span = new Span();
            span.setText(String.valueOf(ChronoUnit.SECONDS.between(item.getStartTimestamp(), ZonedDateTime.now())));
            span.getElement().executeJs("return setInterval(() => {this.innerText = parseInt(this.innerText) + 1}, 1000);")
                    .then(String.class, this.javascriptTimeouts::add);
            return span;
        })).setHeader("Runtime (s)");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal()) {
                final Button button = new Button("Delete");
                button.setDisableOnClick(true);
                button.addClickListener(clickEvent -> {
                    this.evaluationService.deleteEvaluationTask(item.getTaskId());
                    this.update(item.getTaskId());
                });
                return button;
            }

            final Button button = new Button("Cancel");
            button.setDisableOnClick(true);
            button.addClickListener(clickEvent -> {
                this.evaluationService.cancelEvaluationTaskAndNotifyView(item.getTaskId(), true);
                this.update(item.getTaskId());
            });
            return button;
        })).setHeader("Action");

        grid.addColumn(new ComponentRenderer<>(item -> {
            final VerticalLayout layout = new VerticalLayout();
            final Anchor grafanaAnchor = new Anchor();
            grafanaAnchor.setText("Grafana Logs");
            grafanaAnchor.setHref(this.evaluationService.getGrafanaLogsUrl(item));
            grafanaAnchor.setTarget("_blank");
            layout.add(grafanaAnchor);

            final Anchor grafanaResourcesAnchor = new Anchor();
            grafanaResourcesAnchor.setText("Grafana Resources");
            grafanaResourcesAnchor.setHref(this.evaluationService.getGrafanaResourcesUrl(item));
            grafanaResourcesAnchor.setTarget("_blank");
            layout.add(grafanaResourcesAnchor);

            if (item.getStatus().isFinal() && item.isLogsAvailable()) {
                Anchor logsDownloadAnchor = new Anchor();
                logsDownloadAnchor.setText("Logs Download");
                logsDownloadAnchor.setHref("/api/files/download/pattern/logs-" + item.getTaskId());
                logsDownloadAnchor.getElement().setAttribute("download", true);
                layout.add(logsDownloadAnchor);
            }

            return layout;
        })).setHeader("Logs");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal() && item.isResultsAvailable()) {
                final Anchor anchor = new Anchor();
                anchor.setText("Results Download");
                anchor.setHref("/api/files/download/single/results-" + item.getTaskId() + ".txt");
                anchor.getElement().setAttribute("download", true);
                return anchor;
            } else if (item.getStatus().isFinal()) {
                return new Span("Results not available");
            }

            return new Span("");
        })).setHeader("Results");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal() && item.isResultsAvailable()) {
                final Span status = new Span();
                if (item.isResultsCorrect()) {
                    status.setText(String.format("Correct solution (%s)", item.getResultProportion()));
                    status.getElement().getThemeList().add("badge success");
                } else {
                    status.setText(String.format("Incorrect solution (%s)", item.getResultProportion()));
                    status.getElement().getThemeList().add("badge error");
                }

                return status;
            }

            return new Span("");
        })).setHeader("Results Status (correct/total/expected)");

        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        new EvaluationResultEntityContextMenu(grid);
        return grid;
    }

    private static class EvaluationResultEntityContextMenu extends GridContextMenu<EvaluationResultEntity> {
        public EvaluationResultEntityContextMenu(Grid<EvaluationResultEntity> target) {
            super(target);
            addItem("Copy GIT URL to clipboard", e -> e.getItem().ifPresent(person -> {
                UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", person.getGitUrl());
            }));
            addItem("Copy Task ID to clipboard", e -> e.getItem().ifPresent(person -> {
                UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", person.getTaskId());
            }));
        }
    }

    @Override
    public synchronized void dataChanged() {
        this.updateAll();
    }

    @Override
    public synchronized void dataChanged(final String taskId) {
        this.update(taskId);
    }

    private void update(final String taskId) {
        final Optional<EvaluationResultEntity> evaluationResultEntity = evaluationResultRepository.findById(taskId);
        if (evaluationResultEntity.isEmpty()) {
            return;
        }
        getUI().ifPresent(ui -> ui.access(() -> {
            if (this.grid.getListDataView().contains(evaluationResultEntity.get())) {
                this.grid.getListDataView().refreshItem(evaluationResultEntity.get());
            } else {
                this.grid.setItems(evaluationResultRepository.findAll());
            }
        }));
    }

    private void updateAll() {
        getUI().ifPresent(ui -> ui.access(() -> this.grid.setItems(evaluationResultRepository.findAll())));
    }

}
