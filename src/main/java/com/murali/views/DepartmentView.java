package com.murali.views;

import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.service.DepartmentService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;


@Route(value = "add-departments", layout = MainLayout.class)
@PageTitle("Manage Departments")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class DepartmentView extends VerticalLayout {

    private final DepartmentService departmentService;

    private final Grid<Department> grid = new Grid<>(Department.class, false);
    private final Button addBtn = new Button("Add New Department", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Department Name");
    private final TextField hodField = new TextField("Current HOD");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<Department> binder = new BeanValidationBinder<>(Department.class);
    private Department currentDepartment;

    public DepartmentView(DepartmentService departmentService) {
        this.departmentService = departmentService;

        setSizeFull();
        configureGrid();
        configureForm();

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Department()));

        HorizontalLayout toolbar = new HorizontalLayout(new H1("Department Configuration"), addBtn);
        toolbar.setWidthFull();
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(toolbar, grid);

        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(Department::getName).setHeader("Department Name").setSortable(true);

        grid.addColumn(department -> {
            Employee deptHod = department.getHod();
            return deptHod != null ? deptHod.getFirstName() : "Not Assigned";
        }).setHeader("Head of Department");

        grid.addComponentColumn(department -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(department));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmAndDelete(department));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Department Details");

        hodField.setReadOnly(true);
        hodField.setTooltipText("HOD assignment is managed in the Employee section.");

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, hodField);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
        );

        binder.forField(nameField)
                .asRequired("Department Name is required")
                .bind(Department::getName, Department::setName);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveDepartment());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Department department) {
        currentDepartment = department;

        if (department.getId() == null) {
            hodField.setVisible(false);
        } else {
            hodField.setVisible(true);
            if (department.getHod() != null) {
                hodField.setValue(department.getHod().getFirstName());
            } else {
                hodField.setValue("Not Assigned");
            }
        }

        binder.readBean(currentDepartment);
        formDialog.open();
    }

    private void saveDepartment() {
        try {
            binder.writeBean(currentDepartment);
            departmentService.save(currentDepartment);

            showNotification("Department saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();

        } catch (ValidationException e) {
            showNotification("Please check the form for errors", NotificationVariant.LUMO_ERROR);
        } catch (DataIntegrityViolationException e) {
            showNotification("A department with this name already exists.", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("An unexpected error occurred: " + e.getMessage(), NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmAndDelete(Department department) {
        if (departmentService.hasEmployees(department.getId())) {
            openReassignDialog(department);
        } else {
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Delete Department?");
            dialog.setText("Are you sure you want to permanently delete the department '" + department.getName() + "'?");
            dialog.setCancelable(true);
            dialog.setConfirmText("Delete");
            dialog.setConfirmButtonTheme("error primary");
            dialog.addConfirmListener(event -> deleteDepartment(department));
            dialog.open();
        }
    }
    private void deleteDepartment(Department department) {
        try {
            departmentService.delete(department);
            showNotification("Department deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (Exception e) {
            showNotification("Cannot delete. Employees are currently assigned to this department.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateList() {
        grid.setItems(departmentService.findAll());
    }

    private void showNotification(String message, NotificationVariant variant) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
    private void openReassignDialog(Department oldDept) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reassign Employees & Delete");

        com.vaadin.flow.component.html.Paragraph warning = new com.vaadin.flow.component.html.Paragraph(
                "This department has active employees. Select a new department to transfer them to. " +
                        "The current Head of Department will be demoted to a standard employee."
        );
        warning.getStyle().set("color", "var(--lumo-error-text-color)");

        com.vaadin.flow.component.combobox.ComboBox<Department> newDeptCombo = new com.vaadin.flow.component.combobox.ComboBox<>("Select New Department");

        List<Department> availableDepts = departmentService.findAll().stream()
                .filter(d -> !d.getId().equals(oldDept.getId()))
                .collect(java.util.stream.Collectors.toList());

        newDeptCombo.setItems(availableDepts);
        newDeptCombo.setItemLabelGenerator(Department::getName);
        newDeptCombo.setWidthFull();

        Button confirmBtn = new Button("Reassign & Delete");
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.setEnabled(false);

        newDeptCombo.addValueChangeListener(e -> confirmBtn.setEnabled(e.getValue() != null));

        confirmBtn.addClickListener(e -> {
            try {
                departmentService.reassignAndDelete(oldDept, newDeptCombo.getValue());
                showNotification("Employees reassigned and department deleted.", NotificationVariant.LUMO_SUCCESS);
                updateList();
                dialog.close();
            } catch (Exception ex) {
                showNotification("Error during reassignment: " + ex.getMessage(), NotificationVariant.LUMO_ERROR);
            }
        });

        Button cancelBtn = new Button("Cancel", e -> dialog.close());

        dialog.add(warning, newDeptCombo);
        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }
}