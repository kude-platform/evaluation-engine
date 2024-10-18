package com.github.kudeplatform.evaluationengine.view;


import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Route;

/**
 * @author timo.buechert
 */
@Route("/app")
public class AppView extends AppLayout {

    public AppView() {

        DrawerToggle toggle = new DrawerToggle();
        addToNavbar(toggle, new Html("<h1>KUDE <i>/kju:t/</i></h1"));

        SideNav nav = new SideNav();
        SideNavItem evaluationLink = new SideNavItem("Evaluation", EvaluationView.class, VaadinIcon.GLASS.create());
        SideNavItem graphsLink = new SideNavItem("Graphs", GraphView.class, VaadinIcon.CHART.create());
        nav.addItem(evaluationLink, graphsLink);
        addToDrawer(nav);
    }

}
