package com.murali.views;

import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.entity.Role;
import com.murali.entity.User;
import com.murali.service.DepartmentService;
import com.murali.service.EmployeeService;
import com.murali.service.RoleService;
import com.murali.service.UserService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-employee", layout = MainLayout.class)
@PageTitle("Add New Employee")
@RolesAllowed({"Super Admin", "HR Admin"})
public class EmployeeFormView extends VerticalLayout {

    private final EmployeeService employeeService;
    private final DepartmentService deptService;
    private final RoleService roleService;
    private final UserService userService;

    private TextField username = new TextField("Username");
    private PasswordField password = new PasswordField("Password");
    private TextField email = new TextField("Email");
    private ComboBox<Role> role = new ComboBox<>("Security Role");

    private TextField firstName = new TextField("First Name");
    private TextField employeeCode = new TextField("Employee Code");
    private ComboBox<Department> department = new ComboBox<>("Department");
    private ComboBox<Employee> manager = new ComboBox<>("Reporting Manager");

    private Button save = new Button("Register Employee");


    private BeanValidationBinder<User> userBinder = new BeanValidationBinder<>(User.class);
    private BeanValidationBinder<Employee> employeeBinder = new BeanValidationBinder<>(Employee.class);

    public EmployeeFormView(EmployeeService employeeService,
                            DepartmentService deptService,
                            RoleService roleService, UserService userService) {
        this.employeeService = employeeService;
        this.deptService = deptService;
        this.roleService = roleService;
        this.userService = userService;

        configureBinders();

        add(new H3("Employee Credentials"), createIdentityLayout(),
                new H3("Work Information"), createProfileLayout(),
                save);
    }

    private void configureBinders() {
        department.setItems(deptService.findAll());
        department.setItemLabelGenerator(Department::getName);

        role.setItems(roleService.getRoles());
        role.setItemLabelGenerator(Role::getName);

        manager.setItems(employeeService.findAllManagers());
        manager.setItemLabelGenerator(e -> e.getFirstName() + " (" + e.getEmployeeCode() + ")");

        userBinder.bindInstanceFields(this);
        employeeBinder.bindInstanceFields(this);

        userBinder.setBean(new User());
        employeeBinder.setBean(new Employee());

        save.addClickListener(e -> handleSave());

        username.addValueChangeListener(event -> {
            String inputUsername = event.getValue();
            if (inputUsername != null && !inputUsername.isEmpty()) {
                userService.findByUsername(inputUsername).ifPresentOrElse(
                        existingUser -> {
                            userBinder.setBean(existingUser);
                            password.setEnabled(false);
                            email.setEnabled(false);
                            role.setEnabled(false);
                            Notification.show("Existing user found. Linking to employee profile.");
                        },
                        () -> {
                            password.setEnabled(true);
                            email.setEnabled(true);
                            role.setEnabled(true);

                            User newUser = new User();
                            newUser.setUsername(inputUsername);
                            userBinder.setBean(newUser);
                        }
                );
            }
        });

        userBinder.bindInstanceFields(this);
        employeeBinder.bindInstanceFields(this);

        userBinder.setBean(new User());
        employeeBinder.setBean(new Employee());

        save.addClickListener(e -> handleSave());
    }

    private Component createIdentityLayout() {
        FormLayout layout = new FormLayout(username, email, password, role);
        return layout;
    }

    private Component createProfileLayout() {
        FormLayout layout = new FormLayout(firstName, employeeCode, department, manager);
        return layout;
    }

    private void handleSave() {
        if (userBinder.validate().isOk() && employeeBinder.validate().isOk()) {
            try {
                User user = userBinder.getBean();
                Employee employee = employeeBinder.getBean();

                employeeService.createEmployeeWithUser(employee, user);

                Notification.show("Employee and User created successfully!");
                clearForm();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        }
    }

    private void clearForm() {
        userBinder.setBean(new User());
        employeeBinder.setBean(new Employee());
    }
}
