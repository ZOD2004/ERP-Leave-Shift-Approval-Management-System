package com.murali.views.components;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.html.Span;
import java.util.function.Consumer;
public class GlobalSearchComponent extends HorizontalLayout {
    private final TextField searchField;
    private final Icon spinner;

    public GlobalSearchComponent(Consumer<String> onSearch) {
        addClassName("global-search");
        setAlignItems(Alignment.CENTER);
        setSpacing(true);

        searchField = new TextField();
        searchField.setPlaceholder("Search...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(600);
        searchField.setWidthFull();

        spinner = VaadinIcon.SPINNER.create();
        spinner.addClassNames("rotating-spinner", "search-spinner");
        spinner.setVisible(false);

        searchField.addValueChangeListener(e -> {
            showSpinner();
            onSearch.accept(e.getValue());
        });

        add(searchField, spinner);
        setFlexGrow(1, searchField);
    }

    public void showSpinner() {
        spinner.setVisible(true);
    }

    public void hideSpinner() {
        spinner.setVisible(false);
    }
    public void focus() {
        searchField.focus();
    }
}