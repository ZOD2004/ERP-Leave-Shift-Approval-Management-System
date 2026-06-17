package com.murali.views;



import com.murali.entity.Employee;
import com.murali.entity.User;
import com.murali.repository.EmployeeRepository;
import com.murali.service.UserService;
import com.murali.util.SecurityService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("My Profile")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final UserService userService;
    private final SecurityService securityService;

    public ProfileView(UserService userService, SecurityService securityService) {
        this.userService = userService;
        this.securityService = securityService;

        setWidthFull(); setMaxWidth("800px");
        setAlignItems(Alignment.STRETCH);
        addClassNames(LumoUtility.Padding.LARGE, LumoUtility.Margin.Horizontal.AUTO);

        User currentUser = securityService.getAuthenticatedUser();
        Employee currentEmployee = securityService.getCurrentEmployee();

        H2 title = new H2("My Profile");
        title.addClassNames(LumoUtility.Margin.Top.NONE, LumoUtility.Margin.Bottom.LARGE);

        add(title, createDetailsCard(currentUser, currentEmployee), createPasswordCard(currentUser.getUsername()));
    }

    private VerticalLayout createDetailsCard(User user, Employee emp) {
        VerticalLayout card = new VerticalLayout();
        card.addClassNames(
                LumoUtility.Background.BASE,
                LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Padding.LARGE,
                LumoUtility.Margin.Bottom.LARGE,
                LumoUtility.BoxShadow.SMALL
        );

        H3 sectionTitle = new H3("Account Details");
        sectionTitle.addClassNames(LumoUtility.Margin.Top.NONE);

        FormLayout detailsLayout = new FormLayout();
        detailsLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));

        // User Fields
        TextField usernameField = new TextField("Username");
        usernameField.setValue(user.getUsername() != null ? user.getUsername() : "");
        usernameField.setReadOnly(true);

        TextField emailField = new TextField("Email");
        emailField.setValue(user.getEmail() != null ? user.getEmail() : "");
        emailField.setReadOnly(true);

        TextField roleField = new TextField("Role");
        roleField.setValue(user.getRole() != null ? user.getRole().getName() : "N/A");
        roleField.setReadOnly(true);

        detailsLayout.add(usernameField, emailField, roleField);

        // Employee Fields (if linked)
        if (emp != null) {
            TextField empCode = new TextField("Employee Code");
            empCode.setValue(emp.getEmployeeCode() != null ? emp.getEmployeeCode() : "");
            empCode.setReadOnly(true);

            TextField firstName = new TextField("First Name");
            firstName.setValue(emp.getFirstName() != null ? emp.getFirstName() : "");
            firstName.setReadOnly(true);

            TextField dept = new TextField("Department");
            dept.setValue(emp.getDepartment() != null ? emp.getDepartment().getName() : "N/A");
            dept.setReadOnly(true);

            TextField manager = new TextField("Manager");
            manager.setValue(emp.getManager() != null ? emp.getManager().getFirstName() : "None");
            manager.setReadOnly(true);

            detailsLayout.add(empCode, firstName, dept, manager);
        }

        detailsLayout.setWidthFull();
        card.setWidthFull();
        card.add(sectionTitle, new Hr(), detailsLayout);
        return card;
    }

    private VerticalLayout createPasswordCard(String username) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull(); // Constrain card container
        card.addClassNames(
                LumoUtility.Background.BASE,
                LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.LARGE,
                LumoUtility.Padding.LARGE,
                LumoUtility.BoxShadow.SMALL
        );

        H3 sectionTitle = new H3("Security Details");
        sectionTitle.addClassNames(LumoUtility.Margin.Top.NONE);

        FormLayout passwordLayout = new FormLayout();
        // Explicitly make the form fields scale cleanly down to 1 column on tiny viewports
        passwordLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );
        passwordLayout.setWidthFull();

        PasswordField oldPassword = new PasswordField("Current Password");
        PasswordField newPassword = new PasswordField("New Password");
        PasswordField confirmPassword = new PasswordField("Confirm New Password");

        // Explicitly set all individual components to fill their layout cells safely
        oldPassword.setWidthFull();
        newPassword.setWidthFull();
        confirmPassword.setWidthFull();

        Button saveButton = new Button("Update Password", e -> {
            if (newPassword.getValue().isEmpty()) {
                Notification.show("New password cannot be empty.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!newPassword.getValue().equals(confirmPassword.getValue())) {
                Notification.show("New passwords do not match.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            boolean success = userService.changePassword(username, oldPassword.getValue(), newPassword.getValue());

            if (success) {
                Notification.show("Password updated successfully!", 3000, Notification.Position.TOP_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                oldPassword.clear();
                newPassword.clear();
                confirmPassword.clear();
            } else {
                Notification.show("Incorrect current password.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Layout configuration logic:
        passwordLayout.add(oldPassword, newPassword, confirmPassword);

        // Spanning rules:
        passwordLayout.setColspan(oldPassword, 2);     // Takes row 1 entirely
        passwordLayout.setColspan(newPassword, 1);     // Takes row 2, column 1
        passwordLayout.setColspan(confirmPassword, 1); // Takes row 2, column 2

        // Wraps button separately inside a layout container to avoid grid overflow clipping
        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton);
        buttonLayout.setWidthFull();
        buttonLayout.setPadding(false);
        buttonLayout.setMargin(false);

        card.add(sectionTitle, new Hr(), passwordLayout, buttonLayout);
        return card;
    }
}
