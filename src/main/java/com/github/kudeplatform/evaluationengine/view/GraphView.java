package com.github.kudeplatform.evaluationengine.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.Route;

/**
 * @author timo.buechert
 */
@Route(value = "/app/graphs", layout = AppView.class)
public class GraphView extends VerticalLayout implements HasUrlParameter<String> {

    private final TextField jobId = new TextField("Job ID");

    private final IFrame iFrame;

    private final Button loadButton;

    public GraphView() {
        final H2 title = new H2("Performance Graphs");
        this.add(title);

        final HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setPadding(true);
        horizontalLayout.setAlignItems(Alignment.END);

        loadButton = new Button("Load");
        horizontalLayout.add(jobId, loadButton);
        this.add(horizontalLayout);
        this.add(new Hr());


        this.setHeight("100%");
        this.iFrame = new IFrame("");
        iFrame.setHeight("100%");
        iFrame.setWidth("100%");
        iFrame.getElement().setAttribute("frameborder", "1");
        this.add(iFrame);

        loadButton.addClickListener(event -> {
            this.iFrame.setSrc("http://pi-master.local:30611/graph?g0.expr=container_memory_working_set_bytes%7Bimage%3D%22registry.local%2Fakka-tpch-jdk11%3A0.0.1%22%2Cnamespace%3D%22evaluation%22%2Cpod%3D~%22" +
                    "ddm-akka-" + this.jobId.getValue() +
                    ".%2B%22%7D%20or%20kube_pod_container_resource_limits%7Bpod%3D~%22" +
                    "ddm-akka-" + this.jobId.getValue() +
                    ".%2B%22%2C%20resource%3D%22memory%22%7D&g0.tab=0&g0.display_mode=lines&g0.show_exemplars=1&g0.range_input=15m");
            this.iFrame.getStyle().set("zoom", "0.6");
            this.iFrame.reload();
        });
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
        if (parameter == null) {
            return;
        }
        this.jobId.setValue(parameter);
        this.loadButton.click();
    }
}
