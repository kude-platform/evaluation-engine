package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.domain.Dataset;
import com.github.kudeplatform.evaluationengine.service.FileSystemService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;

/**
 * @author timo.buechert
 */
@Route(value = "/app/data", layout = AppView.class)
public class DataView extends VerticalLayout implements NotifiableComponent {

    private final FileSystemService fileSystemService;

    private final Grid<Dataset> datasetGrid = new Grid<>();

    public DataView(final FileSystemService fileSystemService) {
        this.fileSystemService = fileSystemService;
        final H2 title = new H2("Data");
        this.add(title);

        final Upload upload = this.createUploadComponent();
        this.add(upload);

        final Span explanationSpan
                = new Span("Upload a dataset as a ZIP file. The dataset will be synchronized with all nodes asynchronously.");
        this.add(explanationSpan);

        this.add(new Hr());

        final H2 datasetsTitle = new H2("Available Datasets");
        
        datasetGrid.addColumn(Dataset::name).setHeader("Name");
        datasetGrid.addColumn(Dataset::path).setHeader("Path");
        datasetGrid.addColumn(new ComponentRenderer<>(item -> {
            final Button button = new Button("Delete");
            button.setDisableOnClick(true);
            button.addClickListener(clickEvent -> {
                try {
                    this.fileSystemService.deleteDataset(item.name());
                } catch (final Exception e) {
                    showErrorNotification("Could not delete dataset. Error message: " + e.getMessage());
                }
                this.update();
            });
            return button;
        })).setHeader("Action");
        datasetGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        this.add(datasetsTitle, datasetGrid);
        this.update();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.update();
    }

    private Upload createUploadComponent() {
        final MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        final Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".zip");
        upload.addSucceededListener(event -> {

            try {
                fileSystemService.saveDataset(buffer.getInputStream(event.getFileName()), event.getFileName());
            } catch (final Exception e) {
                showErrorNotification("Could not store the uploaded file. The error was: " + e.getMessage());
                return;
            }

            Notification.show("Dataset uploaded successfully.", 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            this.update();
        });

        upload.addFileRejectedListener(event -> showErrorNotification(event.getErrorMessage()));
        return upload;
    }

    private static void showErrorNotification(String errorMessage) {
        Notification.show(errorMessage, 5000, Notification.Position.TOP_CENTER).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    @Override
    public void dataChanged() {
        this.update();
    }

    private void update() {
        getUI().ifPresent(ui -> ui.access(() -> this.datasetGrid.setItems(fileSystemService.getAvailableDatasets())));
    }
}
