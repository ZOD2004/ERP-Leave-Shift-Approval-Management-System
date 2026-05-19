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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "add-user", layout = MainLayout.class)
@PageTitle("Manage Users")
@RolesAllowed({"ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN"})
public class UserView extends VerticalLayout {

    private final UserService userService;
    private final RoleService roleService;

    private final Grid<User> grid = new Grid<>(User.class, false);
    private final Button addBtn = new Button("Add New User", new Icon(VaadinIcon.PLUS));

    private final Dialog formDialog = new Dialog();
    private final TextField username = new TextField("Username");
    private final PasswordField password = new PasswordField("Password");
    private final TextField email = new TextField("Email");
    private final Checkbox active = new Checkbox("Is Active");
    private final ComboBox<Role> role = new ComboBox<>("Role");

    private final Button saveBtn = new Button("Save");
    private final Button cancelBtn = new Button("Cancel");

    private final BeanValidationBinder<User> binder = new BeanValidationBinder<>(User.class);
    private User currentUser;

    public UserView(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;

        setSizeFull();
        configureGrid();
        configureForm();

        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.addClickListener(e -> openForm(new User()));

        H1 title = new H1("User Management");
        title.getStyle().set("margin", "0");

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(toolbar, grid);
        updateList();
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addColumn(User::getUsername).setHeader("Username").setSortable(true);
        grid.addColumn(User::getEmail).setHeader("Email").setSortable(true);
        grid.addColumn(user -> user.getRole() != null ? user.getRole().getName() : "No Role").setHeader("Role");

        grid.addComponentColumn(user -> {
            Icon icon = user.getActive() ? VaadinIcon.CHECK_CIRCLE.create() : VaadinIcon.CLOSE_CIRCLE.create();
            icon.setColor(user.getActive() ? "green" : "red");
            return icon;
        }).setHeader("Active");

        grid.addComponentColumn(user -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            editBtn.addClickListener(e -> openForm(user));

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.addClickListener(e -> confirmAndDelete(user));

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("Actions").setAutoWidth(true);
    }

    private void configureForm() {
        formDialog.setHeaderTitle("User Details");

        role.setItems(roleService.findAll()); // Changed from getRoles() for consistency
        role.setItemLabelGenerator(Role::getName);

        FormLayout formLayout = new FormLayout();
        formLayout.add(username, email, password, role, active);
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        binder.forField(password)
                .bind(User::getPasswordHash, User::setPasswordHash);

        binder.bindInstanceFields(this);

        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickListener(e -> saveUser());

        cancelBtn.addClickListener(e -> formDialog.close());

        formDialog.add(formLayout);
        formDialog.getFooter().add(cancelBtn, saveBtn);
    }

    private void openForm(User user) {
        this.currentUser = user;
        password.setVisible(user.getId() == null);

        binder.readBean(currentUser);
        formDialog.open();
    }

    private void saveUser() {
        try {
            binder.writeBean(currentUser);
            userService.save(currentUser);
            showNotification("User saved successfully", NotificationVariant.LUMO_SUCCESS);
            updateList();
            formDialog.close();
        } catch (ValidationException e) {
            showNotification("Please check the form", NotificationVariant.LUMO_ERROR);
        } catch (Exception e) {
            showNotification("Error: Username/Email already exists", NotificationVariant.LUMO_ERROR);
        }
    }

    private void confirmAndDelete(User user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete User?");
        dialog.setText("Are you sure you want to permanently delete the user '" + user.getUsername() + "'?");

        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");

        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(event -> deleteUser(user));

        dialog.open();
    }

    private void deleteUser(User user) {
        try {
            userService.delete(user);
            showNotification("User deleted", NotificationVariant.LUMO_SUCCESS);
            updateList();
        } catch (Exception e) {
            showNotification("Cannot delete user", NotificationVariant.LUMO_ERROR);
        }
    }

    private void updateList() {
        grid.setItems(userService.findAll());
    }

    private void showNotification(String text, NotificationVariant variant) {
        Notification n = Notification.show(text, 3000, Notification.Position.TOP_CENTER);
        n.addThemeVariants(variant);
    }
}
