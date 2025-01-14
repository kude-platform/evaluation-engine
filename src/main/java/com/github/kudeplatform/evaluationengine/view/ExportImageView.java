package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.service.ImageExportService;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;


/**
 * @author timo.buechert
 */
@Route(value = "/app/exportImages", layout = AppView.class)
public class ExportImageView extends VerticalLayout {

    private final Text statusText = new Text("Status: Idle");

    private final ImageExportService imageExportService;

    private final TextArea textArea = new TextArea();


    @Autowired
    public ExportImageView(final ImageExportService imageExportService) {
        this.imageExportService = imageExportService;

        final H2 title = new H2("Image export view");

        final Button exportImages = new Button("Export images");
        exportImages.addClickListener(event -> this.exportImages());

        this.textArea.setSizeFull();

        this.add(title, new Hr(), exportImages, textArea);
    }


    private void exportImages() {
        try {
            final List<String> imageDownloadLinksForBash = this.imageExportService.getImageDownloadLinksForBash();
            getUI().ifPresent(ui -> ui.access(() -> this.textArea.setValue(String.join("\n", imageDownloadLinksForBash))));
        } catch (final Exception e) {
            Notification.show("An error occurred while exporting images: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

}
