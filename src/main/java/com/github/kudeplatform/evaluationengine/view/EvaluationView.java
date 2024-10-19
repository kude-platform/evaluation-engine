package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.domain.FileEvaluationTask;
import com.github.kudeplatform.evaluationengine.domain.GitEvaluationTask;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationResultRepository;
import com.github.kudeplatform.evaluationengine.service.EvaluationService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.validator.RegexpValidator;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.router.Route;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author timo.buechert
 */
@Route(value = "/app/evaluation", layout = AppView.class)
public class EvaluationView extends VerticalLayout implements NotifiableComponent {

    private final EvaluationResultRepository evaluationResultRepository;

    private final EvaluationService evaluationService;

    private final List<NotifiableComponent> activeViewComponents;

    private final Span uploadSuccessSpan = new Span();

    private final TextField gitRepositoryUrl = new TextField("GIT Repository URL");

    private final TextField additionalCommandLineOptions = new TextField("Additional Command Line Options");

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
                = new Span("Please upload a .jar file or submit a GIT repository URL to start the evaluation.");
        verticalLayout.add(explanationSpan);

        final HorizontalLayout horizontalLayout = new HorizontalLayout();

        horizontalLayout.add(this.createUploadComponent());

        horizontalLayout.add(gitRepositoryUrl);
        gitRepositoryUrl.setRequiredIndicatorVisible(true);
        gitRepositoryUrl.setErrorMessage("This field is required");
        horizontalLayout.add(additionalCommandLineOptions);

        final Binder<GitEvaluationTask> gitBinder = new Binder<>(GitEvaluationTask.class);
        gitBinder.forField(gitRepositoryUrl)
                .withValidator(new StringLengthValidator("GIT Repository URL must contain at least 1 character", 1, null))
                .withValidator(new RegexpValidator("GIT Repository URL must be in a URL format", "https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)"))
                .bind(GitEvaluationTask::repositoryUrl, GitEvaluationTask::setRepositoryUrl);

        final Button submitButton = createSubmitButton(evaluationService, gitBinder);
        horizontalLayout.add(submitButton);
        horizontalLayout.setAlignItems(Alignment.BASELINE);

        verticalLayout.add(horizontalLayout);

        verticalLayout.add(uploadSuccessSpan);

        this.grid = this.createEvaluationTable();
        verticalLayout.add(this.grid);
    }

    @NotNull
    private Button createSubmitButton(EvaluationService evaluationService, Binder<GitEvaluationTask> gitBinder) {
        final Button submitButton = new Button("Submit");
        //submitButton.setDisableOnClick(true);
        submitButton.addClickListener(event -> {
            if (gitBinder.validate().isOk()) {
                final UUID uuid = UUID.randomUUID();
                evaluationService.submitEvaluationTask(new GitEvaluationTask(gitRepositoryUrl.getValue(), uuid,
                        additionalCommandLineOptions.getValue()));

                final Notification notification = Notification.show("Submitted. The Evaluation request will be handled " +
                                "with the following ID: " + uuid + ".",
                        5000, Notification.Position.TOP_CENTER);
                notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
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

    private Upload createUploadComponent() {
        final MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        final Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".jar");
        upload.addSucceededListener(event -> {
            final String fileName = event.getFileName();
            final InputStream in = buffer.getInputStream(fileName);
            final UUID uuid = UUID.randomUUID();

            final File tempFile;
            try {
                tempFile = File.createTempFile(uuid.toString(), ".jar");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            tempFile.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                IOUtils.copy(in, out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            final Notification notification = Notification.show("Upload succeeded. The Evaluation request will be " +
                            "handled " +
                            "with the following ID: " + uuid + ".",
                    5000,
                    Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
            final Anchor anchor = new Anchor("/api/files/" + tempFile.getName(), "Download link for the .jar to " +
                    "validate.");
            anchor.setRouterIgnore(true);
            this.uploadSuccessSpan.removeAll();
            this.uploadSuccessSpan.add(anchor);

            this.evaluationService.submitEvaluationTask(new FileEvaluationTask(uuid, ""));
        });

        upload.addFileRejectedListener(event -> {
            String errorMessage = event.getErrorMessage();
            Notification notification = Notification.show(errorMessage, 5000,
                    Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
        return upload;
    }

    private Grid<EvaluationResultEntity> createEvaluationTable() {
        final Grid<EvaluationResultEntity> grid = new Grid<>(EvaluationResultEntity.class, false);
        grid.addColumn(new ComponentRenderer<>(item -> {
            Anchor anchor = new Anchor();
            anchor.setText(item.getTaskId().toString());
            anchor.setHref("/app/job/" + item.getTaskId().toString());
            return anchor;
        })).setHeader("Task ID");

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
            anchor.setHref("/app/graphs/" + item.getTaskId().toString());
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
                return null;
            }

            final Button button = new Button("Cancel");
            button.setDisableOnClick(true);
            button.addClickListener(clickEvent -> {
                this.evaluationService.cancelEvaluationTask(item.getTaskId());
                this.update();
            });
            return button;
        })).setHeader("Action");

        grid.addColumn(new ComponentRenderer<>(item -> {
            if (item.getStatus().isFinal()) {
                Anchor anchor = new Anchor();
                anchor.setText("Logs Download");
                anchor.setHref("/api/files/logs-" + item.getTaskId().toString() + ".zip");
                anchor.getElement().setAttribute("download", true);
                return anchor;
            }

            return null;
        })).setHeader("Logs");

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
