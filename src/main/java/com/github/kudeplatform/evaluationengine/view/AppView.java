package com.github.kudeplatform.evaluationengine.view;


import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Route;
import org.springframework.boot.info.BuildProperties;

/**
 * @author timo.buechert
 */
@Route("/app")
public class AppView extends AppLayout {

    private final BuildProperties buildProperties;

    public AppView(final BuildProperties buildProperties) {
        this.buildProperties = buildProperties;

        final DrawerToggle toggle = new DrawerToggle();
        final HorizontalLayout navbarContent = new HorizontalLayout();
        final HorizontalLayout logoArea = new HorizontalLayout();
        logoArea.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        final Image image = new Image("images/university-logo.png", "University logo");
        image.setHeight("5vh");
        logoArea.add(image);
        Html html = new Html("<h1>KUDE <i>/kju:t/</i></h1");

        navbarContent.add(toggle, html, logoArea);
        navbarContent.setFlexGrow(1, logoArea);
        navbarContent.setWidth("100%");
        navbarContent.setPadding(true);
        addToNavbar(navbarContent);

        final VerticalLayout navLayout = new VerticalLayout();
        navLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        navLayout.setHeight("100%");
        final SideNav nav = new SideNav();
        final SideNavItem evaluationLink = new SideNavItem("Evaluation", EvaluationView.class, VaadinIcon.GLASS.create());
        final SideNavItem graphsLink = new SideNavItem("Performance Graphs", GraphView.class, VaadinIcon.CHART.create());
        final SideNavItem settingsLink = new SideNavItem("Settings", SettingsView.class, VaadinIcon.COG.create());
        nav.addItem(evaluationLink, graphsLink, settingsLink);
        navLayout.add(nav);

        final Span version = new Span("Version: " + buildProperties.getVersion());
        version.getStyle().set("margin-top", "auto");

        navLayout.add(version);
        addToDrawer(navLayout);

    }

}
