package com.murali.views;

import com.murali.entity.Shift;
import com.murali.service.ShiftService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.Duration;

@Route(value = "add-shifts",layout = MainLayout.class)
@PageTitle("Manage Shifts")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class ShiftView extends VerticalLayout {

    private final ShiftService shiftService;

    private final Grid<Shift> grid = new Grid<>(Shift.class, false);
    private final TextField searchField = new TextField();
    private final Button addBtn = new Button("Add New Shift", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Shift Name");
    private final ComboBox<String> shiftTypeField = new ComboBox<>("Shift Type");
    private final TimePicker startTimeField = new TimePicker("Start Time");
    private final TimePicker endTimeField = new TimePicker("End Time");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<Shift> binder = new BeanValidationBinder<>(Shift.class);
    private Shift currentShift;

    public ShiftView(ShiftService shiftService) {
        this.shiftService = shiftService;

        setSizeFull();
        configureGrid();
        configureForm();

        searchField.setPlaceholder("Search shifts");
        searchField.setTooltipText("Search Using Shift Name");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Shift()));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, searchField);

        add(new H2("Shift Configuration"), toolbar, grid);

        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(Shift::getName).setHeader("Name").setSortable(true);
        grid.addColumn(Shift::getShiftType).setHeader("Type").setSortable(true);
        grid.addColumn(Shift::getStartTime).setHeader("Start Time");
        grid.addColumn(Shift::getEndTime).setHeader("End Time");

        grid.addComponentColumn(shift -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(shift));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> deleteShift(shift));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Shift Details");

        shiftTypeField.setItems("Morning Shift", "Evening Shift", "Night Shift", "Rotational Shift");
        shiftTypeField.setAllowCustomValue(false);

        startTimeField.setStep(Duration.ofMinutes(30));
        endTimeField.setStep(Duration.ofMinutes(30));

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, shiftTypeField, startTimeField, endTimeField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        binder.forField(nameField)
                .asRequired("Shift Name is required")
                .bind(Shift::getName, Shift::setName);

        binder.forField(shiftTypeField)
                .asRequired("Shift Type is required")
                .bind(Shift::getShiftType, Shift::setShiftType);

        binder.forField(startTimeField)
                .asRequired("Start Time is required")
                .bind(Shift::getStartTime, Shift::setStartTime);

        binder.forField(endTimeField)
                .asRequired("End Time is required")
                .bind(Shift::getEndTime, Shift::setEndTime);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveShift());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Shift shift) {
        currentShift = shift;
        binder.readBean(currentShift);
        formDialog.open();
    }

    private void saveShift() {
        try {
            binder.writeBean(currentShift);
            shiftService.addShift(currentShift);

            showNotification("Shift saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();

        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("Failed to save. Duplicate name or database error.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteShift(Shift shift) {
        try {
            shiftService.deleteShift(shift.getId());
            showNotification("Shift deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (Exception e) {
            showNotification("Cannot delete. This shift is assigned to employees.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        if (searchTerm == null || searchTerm.isEmpty()) {
            grid.setItems(shiftService.getShifts());
        } else {
            grid.setItems(shiftService.search(searchTerm));
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}
