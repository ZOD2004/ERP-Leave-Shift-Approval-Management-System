package com.murali.views;

import com.murali.entity.LeaveType;
import com.murali.service.LeaveTypeService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-leave-types",layout = MainLayout.class)
@PageTitle("Manage Leave Types")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class LeaveTypeView extends VerticalLayout {

    private final LeaveTypeService leaveTypeService;

    private final Grid<LeaveType> grid = new Grid<>(LeaveType.class, false);
    private final TextField searchField = new TextField();
    private final Button addBtn = new Button("Add New Leave Type", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Name");
    private final TextField codeField = new TextField("Code");
    private final Checkbox paidCheckbox = new Checkbox("Is Paid");
    private final IntegerField maxDaysField = new IntegerField("Max Days Per Year");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<LeaveType> binder = new BeanValidationBinder<>(LeaveType.class);
    private LeaveType currentLeaveType;

    public LeaveTypeView(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;

        setSizeFull();
        configureGrid();
        configureForm();

        searchField.setPlaceholder("Search by name or code...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> updateList());

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new LeaveType()));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, addBtn);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, searchField);

        add(new H2("Leave Types Configuration"), toolbar, grid);

        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(LeaveType::getName).setHeader("Name").setSortable(true);
        grid.addColumn(LeaveType::getCode).setHeader("Code").setSortable(true);
        grid.addColumn(leaveType -> leaveType.getPaid() ? "Paid" : "Unpaid").setHeader("Status");
        grid.addColumn(LeaveType::getMaxDaysPerYear).setHeader("Max Days");

        grid.addComponentColumn(leaveType -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(leaveType));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> deleteLeaveType(leaveType));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Leave Type Details");

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, codeField, maxDaysField, paidCheckbox);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );
        formLayout.setColspan(paidCheckbox, 2);

        codeField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                codeField.setValue(e.getValue().toUpperCase());
            }
        });

        binder.forField(nameField)
                .asRequired("Name is required")
                .bind(LeaveType::getName, LeaveType::setName);

        binder.forField(codeField)
                .asRequired("Code is required")
                .bind(LeaveType::getCode, LeaveType::setCode);

        binder.forField(maxDaysField)
                .asRequired("Max days is required")
                .withValidator(days -> days >= 0, "Days must be 0 or greater")
                .bind(LeaveType::getMaxDaysPerYear, LeaveType::setMaxDaysPerYear);

        binder.forField(paidCheckbox)
                .bind(LeaveType::getPaid, LeaveType::setPaid);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveLeaveType());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(LeaveType leaveType) {
        currentLeaveType = leaveType;
        binder.readBean(currentLeaveType);
        formDialog.open();
    }

    private void saveLeaveType() {
        try {
            binder.writeBean(currentLeaveType);
            leaveTypeService.addLeaveType(currentLeaveType);

            showNotification("Leave Type saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();

        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("Failed to save. Code or Name might already exist.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void deleteLeaveType(LeaveType leaveType) {
        try {
            leaveTypeService.deleteLeaveType(leaveType.getId());
            showNotification("Leave Type deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (Exception e) {
            showNotification("Cannot delete this type as it is already in use.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateList() {
        String searchTerm = searchField.getValue();
        if (searchTerm == null || searchTerm.isEmpty()) {
            grid.setItems(leaveTypeService.getAllLeaveTypes());
        } else {
            grid.setItems(leaveTypeService.search(searchTerm));
        }
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}
