package com.murali.views.components;

import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class EmptyStateComponent extends VerticalLayout {
    private final H3 heading = new H3();
    private final Span description = new Span();

    public EmptyStateComponent(VaadinIcon icon) {
        setSizeFull();
        setSpacing(true);
        setPadding(false);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName("standard-surface");

        Icon emptyIcon = icon.create();
        emptyIcon.setSize("56px");
        emptyIcon.setColor("var(--app-text-secondary)");

        description.getStyle().set("color", "var(--app-text-secondary)");

        add(emptyIcon, heading, description);
    }

    public void setMessage(String title, String desc) {
        heading.setText(title);
        description.setText(desc);
    }
}
