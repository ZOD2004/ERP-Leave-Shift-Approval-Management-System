package com.murali.views;

import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.service.DepartmentService;
import com.murali.service.EmployeeService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog; // Added import
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

@Route(value = "add-departments", layout = MainLayout.class)
@PageTitle("Manage Departments")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class DepartmentView extends VerticalLayout {

    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    private final Grid<Department> grid = new Grid<>(Department.class, false);
    private final Button addBtn = new Button("Add New Department", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField nameField = new TextField("Department Name");
    private final ComboBox<Employee> hod = new ComboBox<>("Head of Department (HOD)");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final Binder<Department> binder = new BeanValidationBinder<>(Department.class);
    private Department currentDepartment;

    public DepartmentView(DepartmentService departmentService, EmployeeService employeeService) {
        this.departmentService = departmentService;
        this.employeeService = employeeService;

        setSizeFull();
        configureGrid();
        configureForm();

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new Department()));

        HorizontalLayout toolbar = new HorizontalLayout(new H1("Department Configuration"), addBtn);
        toolbar.setWidthFull();

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

            // Replaced direct deletion with the confirmation dialog trigger
            deleteBtn.addClickListener(e -> confirmAndDelete(department));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions");
    }

    private void configureForm() {
        formDialog.setHeaderTitle("Department Details");

        hod.setItems(employeeService.findAllActive());
        hod.setItemLabelGenerator(Employee::getFirstName);
        hod.setPlaceholder("Select HOD (Optional)");
        hod.setClearButtonVisible(true);

        FormLayout formLayout = new FormLayout();
        formLayout.add(nameField, hod);
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1)
        );

        binder.forField(nameField)
                .asRequired("Department Name is required")
                .bind(Department::getName, Department::setName);

        binder.forField(hod)
                .bind(Department::getHod, Department::setHod);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveDepartment());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(Department department) {
        currentDepartment = department;

        hod.setItems(employeeService.findAllActive());

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
        } catch (Exception e) {
            showNotification("Failed to save. Duplicate name or database error.", NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmAndDelete(Department department) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete Department?");
        dialog.setText("Are you sure you want to permanently delete the department '" + department.getName() + "'?");

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(event -> deleteDepartment(department));

        dialog.open();
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
}