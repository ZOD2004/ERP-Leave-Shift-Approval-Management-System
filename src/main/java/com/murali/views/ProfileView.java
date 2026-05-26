package com.murali.views;



import com.murali.entity.Employee;
import com.murali.entity.User;
import com.murali.repository.EmployeeRepository;
import com.murali.service.UserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("My Profile")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;

    public ProfileView(UserService userService, EmployeeRepository employeeRepository) {
        this.userService = userService;
        this.employeeRepository = employeeRepository;

        setSpacing(true);
        setPadding(true);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = userService.findByUsername(username);

        add(new H2("My Profile"));

        // 1. Display User & Employee Details
        createDetailsSection(user);

        add(new Hr());

        // 2. Change Password Section
        createPasswordSection(username);
    }

    private void createDetailsSection(User user) {
        FormLayout detailsLayout = new FormLayout();

        TextField usernameField = new TextField("Username");
        usernameField.setValue(user.getUsername());
        usernameField.setReadOnly(true);

        TextField emailField = new TextField("Email");
        emailField.setValue(user.getEmail());
        emailField.setReadOnly(true);

        TextField roleField = new TextField("Role");
        roleField.setValue(user.getRole().getName());
        roleField.setReadOnly(true);

        detailsLayout.add(usernameField, emailField, roleField);

        // Fetch Employee details if they exist
        Optional<Employee> empOpt = employeeRepository.findByUserId(user.getId());
        if (empOpt.isPresent()) {
            Employee emp = empOpt.get();

            TextField empCode = new TextField("Employee Code");
            empCode.setValue(emp.getEmployeeCode());
            empCode.setReadOnly(true);

            TextField firstName = new TextField("First Name");
            firstName.setValue(emp.getFirstName());
            firstName.setReadOnly(true);

            TextField dept = new TextField("Department");
            dept.setValue(emp.getDepartment() != null ? emp.getDepartment().getName() : "N/A");
            dept.setReadOnly(true);

            TextField manager = new TextField("Manager");
            manager.setValue(emp.getManager() != null ? emp.getManager().getFirstName() : "None");
            manager.setReadOnly(true);

            detailsLayout.add(empCode, firstName, dept, manager);
        }

        add(new H3("Account Details"), detailsLayout);
    }

    private void createPasswordSection(String username) {
        FormLayout passwordLayout = new FormLayout();

        PasswordField oldPassword = new PasswordField("Current Password");
        PasswordField newPassword = new PasswordField("New Password");
        PasswordField confirmPassword = new PasswordField("Confirm New Password");

        Button saveButton = new Button("Update Password", e -> {
            if (newPassword.getValue().isEmpty() || !newPassword.getValue().equals(confirmPassword.getValue())) {
                Notification.show("New passwords do not match or are empty")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            boolean success = userService.changePassword(username, oldPassword.getValue(), newPassword.getValue());

            if (success) {
                Notification.show("Password updated successfully!")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                oldPassword.clear();
                newPassword.clear();
                confirmPassword.clear();
            } else {
                Notification.show("Incorrect current password")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        passwordLayout.add(oldPassword, newPassword, confirmPassword);

        add(new H3("Change Password"), passwordLayout, saveButton);
    }
}
