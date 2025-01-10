package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.service.PlagiarismService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;

import static com.github.kudeplatform.evaluationengine.api.DownloadController.PLAGIARISM_RESULTS_DOWNLOAD_PATH_FULL;


/**
 * @author timo.buechert
 */
@Route(value = "/app/plagiarism", layout = AppView.class)
public class PlagiarismView extends VerticalLayout implements NotifiableComponent {

    private final TextField baseCodeTextField = new TextField("Base code");

    private final TextArea massUploadTextArea = new TextArea();

    private final Button checkButton = new Button("Check");

    private final Anchor downloadAnchor = new Anchor();

    private final Text statusText = new Text("Status: Idle");

    private final PlagiarismService plagiarismService;

    private final List<NotifiableComponent> activePlagiarismViewComponents;

    @Autowired
    public PlagiarismView(final PlagiarismService plagiarismService,
                          @Qualifier(value = "activePlagiarismViewComponents") final List<NotifiableComponent> activePlagiarismViewComponents) {
        final H2 title = new H2("Plagiarism");
        final Anchor anchor = new Anchor();
        anchor.setText("Results Download");
        anchor.setHref(PLAGIARISM_RESULTS_DOWNLOAD_PATH_FULL);
        anchor.getElement().setAttribute("download", true);

        final Text explanationText = new Text("To view the results of the plagiarism check, click the download link above and use the JPlag UI under the following link: ");
        final Anchor jplagAnchor = new Anchor("https://jplag.github.io/JPlag/", "JPlag");
        jplagAnchor.setTarget("_blank");

        this.plagiarismService = plagiarismService;
        this.activePlagiarismViewComponents = activePlagiarismViewComponents;

        this.baseCodeTextField.setPlaceholder("Enter base code repository here");
        this.baseCodeTextField.setWidth("25%");
        this.massUploadTextArea.setPlaceholder("Paste the content of the mass upload file here. The content must be in CSV format following the pattern: " +
                "repositoryUrl;name");
        this.massUploadTextArea.setLabel("Repositories to check in CSV format");
        this.massUploadTextArea.setSizeFull();
        this.checkButton.addClickListener(event -> this.performCheck());

        this.add(title, anchor, explanationText, jplagAnchor, new Hr(), this.statusText, this.checkButton, this.baseCodeTextField, this.massUploadTextArea);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        synchronized (this.activePlagiarismViewComponents) {
            this.activePlagiarismViewComponents.add(this);
        }

        this.dataChanged();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        synchronized (this.activePlagiarismViewComponents) {
            this.activePlagiarismViewComponents.remove(this);
        }
    }

    @Override
    public void dataChanged() {
        getUI().ifPresent(ui -> ui.access(() -> this.statusText.setText(plagiarismService.getCurrentStatus())));
    }

    @Override
    public void dataChanged(String taskId) {
        this.dataChanged();
    }

    private void performCheck() {
        try {
            this.plagiarismService.checkPlagiarism(this.baseCodeTextField.getValue(), this.massUploadTextArea.getValue());
        } catch (final Exception e) {
            Notification.show("An error occurred while checking for plagiarism: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

}
