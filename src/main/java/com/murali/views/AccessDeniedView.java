package com.murali.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.AccessDeniedException;

public class AccessDeniedView extends VerticalLayout implements HasErrorParameter<AccessDeniedException> {

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<AccessDeniedException> parameter) {

        removeAll();

        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSizeFull();

        H1 header = new H1("403 - Access Denied");
        Paragraph message = new Paragraph("You do not have the required permissions to view this page.");

        RouterLink homeLink = new RouterLink("Return to Dashboard", DashboardView.class);

        add(header, message, homeLink);
        return 403;
    }
}
