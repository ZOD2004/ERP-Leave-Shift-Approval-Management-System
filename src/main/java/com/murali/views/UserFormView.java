package com.murali.views;

import com.murali.entity.Role;
import com.murali.entity.User;
import com.murali.exception.UserAlreadyExistException;
import com.murali.service.RoleService;
import com.murali.service.UserService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-user",layout = MainLayout.class)
@PageTitle("Add New User")
@RolesAllowed({"Super Admin","HR Admin"})
public class UserFormView extends VerticalLayout {

    private final UserService userService;
    private final RoleService roleService;

    private TextField username = new TextField("Username");
    private PasswordField password = new PasswordField("Password");
    private TextField email = new TextField("Email");
    private Checkbox active = new Checkbox("Is Active");
    private ComboBox<Role> role = new ComboBox<>("Role");

    private Button save = new Button("Save User");
    private Button cancel = new Button("Cancel");

    private BeanValidationBinder<User> binder = new BeanValidationBinder<>(User.class);

    public UserFormView(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);

        configureForm();
        add(createFormLayout());

        binder.setBean(new User());
    }

    private void configureForm() {

        role.setItems(roleService.getRoles());
        role.setItemLabelGenerator(Role::getName);

        binder.forField(role)
                .asRequired("Please select a role")
                .bind(User::getRole, User::setRole);

        binder.forField(password)
                .bind(User::getPasswordHash, User::setPasswordHash);

        binder.bindInstanceFields(this);

        save.addClickListener(event -> saveUser());

        cancel.addClickListener(event -> binder.setBean(new User()));
    }

    private Component createFormLayout() {
        FormLayout formLayout = new FormLayout();
        formLayout.add(username, email, password, role, active);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        formLayout.setMaxWidth("400px");

        HorizontalLayout buttons = new HorizontalLayout(save, cancel);
        return new VerticalLayout(formLayout, buttons);
    }

    private void saveUser() {
        if (binder.validate().isOk()) {
            User newUser = binder.getBean();
            try {
                userService.addUser(newUser);
            } catch (Exception e) {
                throw new UserAlreadyExistException("The User Name "+newUser.getUsername()+" is already in use try some other username");
            }
            Notification.show("User added successfully!");
            binder.setBean(new User());
        }
    }
}
