package com.github.kudeplatform.evaluationengine.view;

import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventEntity;
import com.github.kudeplatform.evaluationengine.persistence.EvaluationEventRepository;
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
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

/**
 * @author timo.buechert
 */
@Route(value = "/app/job", layout = AppView.class)
public class JobView extends VerticalLayout implements HasUrlParameter<String> {

    @Autowired
    private EvaluationEventRepository evaluationEventRepository;

    private final Span jobNameSpan = new Span();

    private String jobName;

    private Grid<EvaluationEventEntity> grid;

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
        grid.addColumn(EvaluationEventEntity::getTaskId).setHeader("Task ID");
        grid.addColumn(evaluationResultEntity -> evaluationResultEntity.getTimestamp().toString()).setHeader("Timestamp");
        grid.addColumn(EvaluationEventEntity::getMessage).setHeader("Message");
        grid.addColumn(evaluationResultEntity -> evaluationResultEntity.getStatus().toString()).setHeader("Status");

        grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
        this.add(grid);
    }

    @Scheduled(fixedRate = 5000)
    public void update() {
        getUI().ifPresent(ui -> ui.access(() -> {
            this.grid.setItems(this.evaluationEventRepository.findByTaskId(UUID.fromString(this.jobName)));
        }));
    }


}
