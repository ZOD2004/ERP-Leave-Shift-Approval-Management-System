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
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class EmployeeFormView extends VerticalLayout {

    private final EmployeeService employeeService;
    private final DepartmentService deptService;
    private final RoleService roleService;
    private final UserService userService;

    // UI Components
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

        configureFields();
        configureBinders();

        add(new H3("Employee Credentials"), createIdentityLayout(),
                new H3("Work Information"), createProfileLayout(),
                save);

        clearForm();
    }

    private void configureFields() {
        department.setItems(deptService.findAll());
        department.setItemLabelGenerator(Department::getName);

        role.setItems(roleService.getRoles());
        role.setItemLabelGenerator(Role::getName);

        manager.setItems(employeeService.findAllManagers());
        manager.setItemLabelGenerator(e -> e.getFirstName() + " (" + e.getEmployeeCode() + ")");

        save.addClickListener(e -> handleSave());

        username.addValueChangeListener(event -> {
            String inputUsername = event.getValue();
            if (inputUsername != null && !inputUsername.isEmpty()) {
                userService.findByUsername(inputUsername).ifPresentOrElse(
                        existingUser -> {
                            userBinder.readBean(existingUser);
                            toggleUserFields(false);
                            Notification.show("Existing user found. Linking to employee profile.");
                        },
                        () -> {
                            toggleUserFields(true);
                        }
                );
            }
        });
    }

    private void configureBinders() {
        userBinder.forField(password)
                .asRequired("Password is required for new users")
                .bind(User::getPasswordHash, User::setPasswordHash);

        userBinder.bindInstanceFields(this);

        employeeBinder.bindInstanceFields(this);
    }

    private void toggleUserFields(boolean enabled) {
        password.setEnabled(enabled);
        email.setEnabled(enabled);
        role.setEnabled(enabled);
        if (enabled) {
            password.clear();
            email.clear();
            role.clear();
        }
    }

    private Component createIdentityLayout() {
        return new FormLayout(username, email, password, role);
    }

    private Component createProfileLayout() {
        return new FormLayout(firstName, employeeCode, department, manager);
    }

    private void handleSave() {
        User user = new User();
        Employee employee = new Employee();

        boolean isUserValid = userBinder.writeBeanIfValid(user);
        boolean isEmployeeValid = employeeBinder.writeBeanIfValid(employee);

        if (isUserValid && isEmployeeValid) {
            try {
                employeeService.createEmployeeWithUser(employee, user);
                Notification.show("Employee and User created successfully!");
                clearForm();
            } catch (Exception ex) {
                Notification.show("Error: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        } else {
            Notification.show("Please fix the errors in the form.");
        }
    }

    private void clearForm() {
        userBinder.readBean(new User());
        employeeBinder.readBean(new Employee());
        toggleUserFields(true);
    }
}