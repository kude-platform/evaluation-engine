package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
import com.github.kudeplatform.evaluationengine.service.HintsService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author timo.buechert
 */
@Route(value = "/app/job", layout = AppView.class)
public class JobView extends VerticalLayout implements HasUrlParameter<String>, NotifiableComponent {

    @Autowired
    private EvaluationEventRepository evaluationEventRepository;

    @Autowired
    private HintsService hintsService;

    private final Span jobNameSpan = new Span();

    private String jobName;

    private Grid<EvaluationEventEntity> grid;

    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.GERMAN);

    public JobView() {
        final H2 title = new H2("Job");
        this.add(title);

    }

    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        this.jobName = parameter;
        this.jobNameSpan.setText("Job: " + parameter);
        this.add(jobName);
        this.addEvaluationTable();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.update();
    }

    private void addEvaluationTable() {
        this.grid = new Grid<>(EvaluationEventEntity.class, false);
        grid.addColumn(EvaluationEventEntity::getIndex).setHeader("Instance Index");
        grid.addColumn(evaluationResultEntity -> dateTimeFormatter.format(evaluationResultEntity.getTimestamp())).setHeader("Timestamp");
        grid.addColumn(EvaluationEventEntity::getType).setHeader("Type");
        grid.addColumn(EvaluationEventEntity::getMessage).setHeader("Message");
        grid.addColumn(evaluationEventEntity -> hintsService.getHintForCategory(evaluationEventEntity.getType())).setHeader("Hint");

        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        this.add(grid);
    }

    public void update() {
        getUI().ifPresent(ui -> ui.access(() -> this.grid.setItems(this.evaluationEventRepository.findByTaskId(this.jobName))));
    }


    @Override
    public void dataChanged() {
        this.update();
    }

    @Override
    public void dataChanged(final String taskId) {
        this.update();
    }
}
