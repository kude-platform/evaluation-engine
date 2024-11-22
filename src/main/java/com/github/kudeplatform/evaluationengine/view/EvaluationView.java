package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.domain.GitEvaluationTask;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author timo.buechert
 */
@Route(value = "/app/evaluation", layout = AppView.class)
@Slf4j
public class EvaluationView extends VerticalLayout implements NotifiableComponent {

    private final EvaluationResultRepository evaluationResultRepository;

    private final EvaluationService evaluationService;

    private final List<NotifiableComponent> activeViewComponents;

    private final TextField gitRepositoryUrl = new TextField("GIT Repository URL");

    private final TextField name = new TextField("Name");

    private final TextField additionalCommandLineOptions =
            new TextField("Additional Command Line Options", "-kb true", "");

    private final Grid<EvaluationResultEntity> grid;

    private final List<String> javascriptTimeouts = new ArrayList<>();

    @Autowired
    public EvaluationView(final EvaluationResultRepository evaluationResultRepository,
                          final EvaluationService evaluationService,
                          final List<NotifiableComponent> activeViewComponents) {
        this.evaluationResultRepository = evaluationResultRepository;
        this.evaluationService = evaluationService;
        this.activeViewComponents = activeViewComponents;

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

        horizontalLayout.add(name);
        name.setRequiredIndicatorVisible(true);
        name.setErrorMessage("This field is required");
        name.setTooltipText("Your name or a descriptive name for the evaluation task");

        horizontalLayout.add(additionalCommandLineOptions);

        final Binder<GitEvaluationTask> gitBinder = new Binder<>(GitEvaluationTask.class);
        gitBinder.forField(gitRepositoryUrl)
                .withValidator(new StringLengthValidator("GIT Repository URL must contain at least 1 character", 1, null))
                .withValidator(new RegexpValidator("GIT Repository URL must be in a URL format", "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"))
                .bind(GitEvaluationTask::repositoryUrl, GitEvaluationTask::setRepositoryUrl);

        final Binder<GitEvaluationTask> nameBinder = new Binder<>(GitEvaluationTask.class);
        nameBinder.forField(name)
                .withValidator(new StringLengthValidator("Name must contain at least 1 character", 1, null))
                .bind(GitEvaluationTask::name, GitEvaluationTask::setName);

        final Button submitButton = createSubmitButton(evaluationService, gitBinder, nameBinder);
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

        verticalLayout.add(horizontalLayout);

        Span uploadSuccessSpan = new Span();
        verticalLayout.add(uploadSuccessSpan);

        this.grid = this.createEvaluationTable();
        verticalLayout.add(this.grid);
    }

    @NotNull
    private Button createSubmitButton(EvaluationService evaluationService, Binder<GitEvaluationTask> gitBinder,
                                      Binder<GitEvaluationTask> nameBinder) {
        final Button submitButton = new Button("Submit");
        submitButton.addClickShortcut(Key.ENTER);
        submitButton.addClickListener(event -> {
            if (gitBinder.validate().isOk() && nameBinder.validate().isOk()) {
                final String uuid = UUID.randomUUID().toString();
                evaluationService.submitEvaluationTask(new GitEvaluationTask(gitRepositoryUrl.getValue(), uuid,
                        additionalCommandLineOptions.getValue(), name.getValue()), true);

                final Notification notification = Notification.show("Submitted. The Evaluation request will be handled " +
                                "with the following ID: " + uuid + ".",
                        5000, Notification.Position.TOP_CENTER);
                notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
                submitButton.setEnabled(false);
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        log.error("Thread interrupted", e);
                    }
                    getUI().ifPresent(ui -> ui.access(() -> submitButton.setEnabled(true)));
                }).start();
            }

        });
        return submitButton;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.update();
        synchronized (this.activeViewComponents) {
            this.activeViewComponents.add(this);
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        synchronized (this.activeViewComponents) {
            this.activeViewComponents.remove(this);
        }
        this.javascriptTimeouts.forEach(s -> UI.getCurrent().getPage().executeJs("clearInterval(" + s + ");console.error('cleared " + s + "');"));
    }

    private VerticalLayout createMassUploadDialogLayout(Dialog dialog) {
        TextArea massUpload = new TextArea();
        massUpload.setPlaceholder("Paste the content of the mass upload file here. The content must be in CSV format following the pattern: " +
                "repositoryUrl;name");
        massUpload.setLabel("Mass Upload CSV Content");
        massUpload.setSizeFull();

        VerticalLayout fieldLayout = new VerticalLayout(massUpload);
        fieldLayout.setSpacing(false);
        fieldLayout.setPadding(false);
        fieldLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        fieldLayout.setSizeFull();

        Button saveButton = new Button("Evaluate", e -> {
            if (massUpload.isEmpty()) {
                Notification.show("Please provide a valid CSV content", 5000, Notification.Position.MIDDLE);
            } else {
                dialog.close();
                this.evaluationService.submitMassEvaluationTask(massUpload.getValue(), this.additionalCommandLineOptions.getValue());
            }
        });
        Button cancelButton = new Button("Cancel", e -> dialog.close());
        dialog.getFooter().add(cancelButton);
        dialog.getFooter().add(saveButton);

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

        grid.addColumn(EvaluationResultEntity::getName).setHeader("Name");

        grid.addColumn(evaluationResultEntity -> {
            final int positionInQueue = this.evaluationService.getPositionInQueue(evaluationResultEntity.getTaskId());
            return positionInQueue < 0 ? "" : positionInQueue;
        }).setHeader("Position In Queue");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isPending()) {
                return null;
            }
            Anchor anchor = new Anchor();
            anchor.setText("Performance Graphs");
            anchor.setHref("/app/graphs/" + item.getTaskId());
            return anchor;
        })).setHeader("Performance Graphs");

        grid.addColumn(evaluationResultEntity -> evaluationResultEntity.getStatus().toString()).setHeader("Status");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (!item.getStatus().isRunning()) {
                return null;
            }
            final Span span = new Span();
            span.setText(String.valueOf(ChronoUnit.SECONDS.between(item.getTimestamp(), ZonedDateTime.now())));
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
                    this.update();
                });
                return button;
            }

            final Button button = new Button("Cancel");
            button.setDisableOnClick(true);
            button.addClickListener(clickEvent -> {
                this.evaluationService.cancelEvaluationTask(item.getTaskId(), true);
                this.update();
            });
            return button;
        })).setHeader("Action");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal() && item.isLogsAvailable()) {
                Anchor anchor = new Anchor();
                anchor.setText("Logs Download");
                anchor.setHref("/api/files/download/pattern/logs-" + item.getTaskId());
                anchor.getElement().setAttribute("download", true);
                return anchor;
            } else if (item.getStatus().isFinal()) {
                return new Span("Logs not available");
            }

            return null;
        })).setHeader("Logs");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal() && item.isResultsAvailable()) {
                Anchor anchor = new Anchor();
                anchor.setText("Results Download");
                anchor.setHref("/api/files/download/single/results-" + item.getTaskId() + ".txt");
                anchor.getElement().setAttribute("download", true);
                return anchor;
            } else if (item.getStatus().isFinal()) {
                return new Span("Results not available");
            }

            return null;
        })).setHeader("Results");

        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        return grid;
    }

    @Override
    public void dataChanged() {
        this.update();
    }

    private void update() {
        getUI().ifPresent(ui -> ui.access(() -> this.grid.setItems(evaluationResultRepository.findAll())));
    }


}
