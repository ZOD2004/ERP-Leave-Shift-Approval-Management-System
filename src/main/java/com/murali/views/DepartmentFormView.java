package com.murali.views;

import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.service.DepartmentService;
import com.murali.service.EmployeeService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-department", layout = MainLayout.class)
@PageTitle("Manage Departments")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class DepartmentFormView extends VerticalLayout {

    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    private TextField name = new TextField("Department Name");
    private ComboBox<Employee> hod = new ComboBox<>("Head of Department (HOD)");
    private Button save = new Button("Save Department");

    private BeanValidationBinder<Department> binder = new BeanValidationBinder<>(Department.class);

    public DepartmentFormView(DepartmentService departmentService, EmployeeService employeeService) {
        this.departmentService = departmentService;
        this.employeeService = employeeService;

        hod.setItems(employeeService.findAll());
        hod.setItemLabelGenerator(Employee::getFirstName);
        hod.setPlaceholder("Select HOD (Optional)");

        binder.bindInstanceFields(this);
        binder.setBean(new Department());

        add(new H3("Department Details"), new FormLayout(name, hod), save);

        save.addClickListener(e -> {
            if (binder.validate().isOk()) {
                departmentService.save(binder.getBean());
                Notification.show("Department Saved!");
//                binder.setBean(new Department());
            }
        });
    }
}
